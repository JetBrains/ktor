/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.events.EventDefinition
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.internal.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

public actual class EmbeddedServer<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >
actual constructor(
    private val applicationProperties: ApplicationProperties,
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
    engineConfigBlock: TConfiguration.() -> Unit
) {

    public actual val monitor: Events = Events()

    public actual val environment: ApplicationEnvironment = applicationProperties.environment

    public actual val application: Application
        get() = currentApplication()

    public actual val engineConfig: TConfiguration = engineFactory.configuration(engineConfigBlock)
    private val applicationInstanceLock = ReentrantReadWriteLock()
    private var recreateInstance: Boolean = false
    private var _applicationClassLoader: ClassLoader? = null
    private var packageWatchKeys = emptyList<WatchKey>()

    private val configuredWatchPath = environment.config.propertyOrNull("ktor.deployment.watch")?.getList().orEmpty()
    private val watchPatterns: List<String> = configuredWatchPath + applicationProperties.watchPaths

    private val configModulesNames: List<String> = run {
        environment.config.propertyOrNull("ktor.application.modules")?.getList() ?: emptyList()
    }

    private val modulesNames: List<String> = configModulesNames

    private var _applicationInstance: Application? = Application(
        environment,
        applicationProperties.developmentMode,
        applicationProperties.rootPath,
        monitor,
        applicationProperties.parentCoroutineContext,
        ::engine
    )

    public actual val engine: TEngine = engineFactory.create(
        environment,
        monitor,
        applicationProperties.developmentMode,
        engineConfig,
        ::currentApplication
    )

    private val watcher: WatchService? by lazy {
        try {
            FileSystems.getDefault().newWatchService()
        } catch (_: NoClassDefFoundError) {
            null
        }
    }

    /**
     * Reload application: destroy it first and then create again
     */
    public fun reload() {
        applicationInstanceLock.write {
            destroyApplication()
            val (application, classLoader) = createApplication()
            _applicationInstance = application
            _applicationClassLoader = classLoader
        }
    }

    private fun currentApplication(): Application = applicationInstanceLock.read {
        val currentApplication = _applicationInstance ?: error("EmbeddedServer was stopped")

        if (!applicationProperties.developmentMode) {
            return@read currentApplication
        }

        val changes = packageWatchKeys.flatMap { it.pollEvents() }
        if (changes.isEmpty()) {
            return@read currentApplication
        }

        environment.log.info("Changes in application detected.")

        var count = changes.size
        while (true) {
            Thread.sleep(200)
            val moreChanges = packageWatchKeys.flatMap { it.pollEvents() }
            if (moreChanges.isEmpty()) {
                break
            }

            environment.log.debug("Waiting for more changes.")
            count += moreChanges.size
        }

        environment.log.debug("Changes to $count files caused application restart.")
        changes.take(5).forEach { environment.log.debug("...  ${it.context()}") }

        applicationInstanceLock.write {
            destroyApplication()
            val (application, classLoader) = createApplication()
            _applicationInstance = application
            _applicationClassLoader = classLoader
        }

        return@read _applicationInstance ?: error("EmbeddedServer was stopped")
    }

    private fun createApplication(): Pair<Application, ClassLoader> {
        val classLoader = createClassLoader()
        val currentThread = Thread.currentThread()
        val oldThreadClassLoader = currentThread.contextClassLoader
        currentThread.contextClassLoader = classLoader

        try {
            return instantiateAndConfigureApplication(classLoader) to classLoader
        } finally {
            currentThread.contextClassLoader = oldThreadClassLoader
        }
    }

    @Suppress("DEPRECATION")
    private fun createClassLoader(): ClassLoader {
        val baseClassLoader = environment.classLoader

        if (!applicationProperties.developmentMode) {
            environment.log.info("Autoreload is disabled because the development mode is off.")
            return baseClassLoader
        }

        val watchPatterns = watchPatterns
        if (watchPatterns.isEmpty()) {
            environment.log.info("No ktor.deployment.watch patterns specified, automatic reload is not active.")
            return baseClassLoader
        }

        val allUrls = baseClassLoader.allURLs()
        val jre = File(System.getProperty("java.home")).parent
        val debugUrls = allUrls.map { it.file }
        environment.log.debug("Java Home: $jre")
        environment.log.debug("Class Loader: $baseClassLoader: ${debugUrls.filter { !it.toString().startsWith(jre) }}")

        // we shouldn't watch URL for ktor-server classes, even if they match patterns,
        // because otherwise it loads two ApplicationEnvironment (and other) types which do not match
        val coreUrls = listOf(
            ApplicationEnvironment::class.java, // ktor-server
            Pipeline::class.java, // ktor-parsing
            HttpStatusCode::class.java, // ktor-http
            kotlin.jvm.functions.Function1::class.java, // kotlin-stdlib
            Logger::class.java, // slf4j
            ByteReadChannel::class.java,
            Input::class.java, // kotlinx-io
            Attributes::class.java
        ).mapNotNullTo(HashSet()) { it.protectionDomain.codeSource.location }

        val watchUrls = allUrls.filter { url ->
            url !in coreUrls && watchPatterns.any { pattern -> url.toString().contains(pattern) } &&
                !(url.path ?: "").startsWith(jre)
        }

        if (watchUrls.isEmpty()) {
            environment.log.info(
                "No ktor.deployment.watch patterns match classpath entries, automatic reload is not active"
            )
            return baseClassLoader
        }

        watchUrls(watchUrls)
        return OverridingClassLoader(watchUrls, baseClassLoader)
    }

    private fun safeRaiseEvent(event: EventDefinition<Application>, application: Application) {
        monitor.raiseCatching(event, application)
    }

    private fun destroyApplication() {
        val currentApplication = _applicationInstance
        val applicationClassLoader = _applicationClassLoader
        _applicationInstance = null
        _applicationClassLoader = null

        if (currentApplication != null) {
            safeRaiseEvent(ApplicationStopping, currentApplication)
            try {
                currentApplication.dispose()
                (applicationClassLoader as? OverridingClassLoader)?.close()
            } catch (e: Throwable) {
                environment.log.error("Failed to destroy application instance.", e)
            }

            safeRaiseEvent(ApplicationStopped, currentApplication)
        }
        packageWatchKeys.forEach { it.cancel() }
        packageWatchKeys = mutableListOf()
    }

    private fun watchUrls(urls: List<URL>) {
        val paths = HashSet<Path>()
        for (url in urls) {
            val path = url.path ?: continue
            val decodedPath = URLDecoder.decode(path, "utf-8")
            val folder = runCatching { File(decodedPath).toPath() }.getOrNull() ?: continue

            if (!Files.exists(folder)) {
                continue
            }

            val visitor = object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    paths.add(dir)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val dir = file.parent
                    if (dir != null) {
                        paths.add(dir)
                    }

                    return FileVisitResult.CONTINUE
                }
            }

            if (Files.isDirectory(folder)) {
                Files.walkFileTree(folder, visitor)
            }
        }

        paths.forEach { path ->
            environment.log.debug("Watching $path for changes.")
        }

        val modifiers = get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH()?.let { arrayOf(it) } ?: emptyArray()
        packageWatchKeys = paths.mapNotNull { path ->
            watcher?.let {
                path.register(it, arrayOf(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), *modifiers)
            }
        }
    }

    public actual fun start(wait: Boolean): EmbeddedServer<TEngine, TConfiguration> {
        applicationInstanceLock.write {
            val (application, classLoader) = try {
                createApplication()
            } catch (cause: Throwable) {
                destroyApplication()
                if (watchPatterns.isNotEmpty()) {
                    cleanupWatcher()
                }

                throw cause
            }
            _applicationInstance = application
            _applicationClassLoader = classLoader
        }

        CoroutineScope(application.coroutineContext).launch {
            engine.resolvedConnectors().forEach {
                val host = escapeHostname(it.host)
                environment.log.info(
                    "Responding at ${it.type.name.lowercase()}://$host:${it.port}"
                )
            }
        }

        engine.start(wait)
        return this
    }

    public fun stop(shutdownGracePeriod: Long, shutdownTimeout: Long, timeUnit: TimeUnit) {
        engine.stop(timeUnit.toMillis(shutdownGracePeriod), timeUnit.toMillis(shutdownTimeout))
        applicationInstanceLock.write {
            destroyApplication()
        }
        if (watchPatterns.isNotEmpty()) {
            cleanupWatcher()
        }
    }

    public actual fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        stop(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
    }

    private fun instantiateAndConfigureApplication(currentClassLoader: ClassLoader): Application {
        val newInstance = if (recreateInstance || _applicationInstance == null) {
            Application(
                environment,
                applicationProperties.developmentMode,
                applicationProperties.rootPath,
                monitor,
                applicationProperties.parentCoroutineContext,
                ::engine
            )
        } else {
            recreateInstance = true
            _applicationInstance!!
        }

        safeRaiseEvent(ApplicationStarting, newInstance)

        avoidingDoubleStartup {
            modulesNames.forEach { name ->
                launchModuleByName(name, currentClassLoader, newInstance)
            }

            applicationProperties.modules.forEach { module ->
                val name = module.methodName()

                try {
                    launchModuleByName(name, currentClassLoader, newInstance)
                } catch (_: ReloadingException) {
                    module(newInstance)
                }
            }
        }

        safeRaiseEvent(ApplicationStarted, newInstance)
        return newInstance
    }

    private fun launchModuleByName(name: String, currentClassLoader: ClassLoader, newInstance: Application) {
        avoidingDoubleStartupFor(name) {
            executeModuleFunction(currentClassLoader, name, newInstance)
        }
    }

    private fun avoidingDoubleStartup(block: () -> Unit) {
        try {
            block()
        } finally {
            currentStartupModules.get()?.let {
                if (it.isEmpty()) {
                    currentStartupModules.remove()
                }
            }
        }
    }

    private fun avoidingDoubleStartupFor(fqName: String, block: () -> Unit) {
        val modules = currentStartupModules.getOrSet { ArrayList(1) }
        check(!modules.contains(fqName)) {
            "Module startup is already in progress for function $fqName (recursive module startup from module main?)"
        }

        modules.add(fqName)
        try {
            block()
        } finally {
            modules.remove(fqName)
        }
    }

    private fun cleanupWatcher() {
        try {
            watcher?.close()
        } catch (_: NoClassDefFoundError) {
        }
    }
}
