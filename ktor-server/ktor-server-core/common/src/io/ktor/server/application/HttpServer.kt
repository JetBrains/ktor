/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * A builder for [ApplicationRuntimeConfig].
 */
public class ApplicationRuntimeConfigBuilder(
    public val environment: ApplicationEnvironment
) {

    internal val modules: MutableList<ServerModule> = mutableListOf()

    /**
     * Paths to wait for application reload.
     */
    public var watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH)

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    public var rootPath: String = ""

    /**
     * Indicates whether development mode is enabled.
     */
    public var developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE

    /**
     * Parent coroutine context for an application.
     */
    public var parentCoroutineContext: CoroutineContext = EmptyCoroutineContext

    /**
     * Installs an application module.
     */
    public fun module(body: ServerModule) {
        modules.add(body)
    }

    internal fun build(): ApplicationRuntimeConfig =
        ApplicationRuntimeConfig(environment, modules, watchPaths, rootPath, developmentMode, parentCoroutineContext)
}

/**
 * An application config with which the application is running.
 */
public class ApplicationRuntimeConfig internal constructor(
    public val environment: ApplicationEnvironment,
    internal val modules: MutableList<ServerModule>,
    internal val watchPaths: List<String>,
    public val rootPath: String,
    public val developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE,
    parentCoroutineContext: CoroutineContext
) {
    private val bridge = ApplicationPropertiesBridge(this, parentCoroutineContext)
    public val parentCoroutineContext: CoroutineContext = bridge.parentCoroutineContext
}

/**
 * Creates an [ApplicationRuntimeConfig] instance.
 */
public fun applicationRuntimeConfig(
    environment: ApplicationEnvironment = applicationEnvironment {},
    block: ApplicationRuntimeConfigBuilder.() -> Unit = {}
): ApplicationRuntimeConfig {
    return ApplicationRuntimeConfigBuilder(environment).apply(block).build()
}

/**
 * Typealias for compatibility.
 */
public typealias Application = HttpServer

/**
 * Cleaner name for modules supplied to the server.
 */
public typealias ServerModule = HttpServer.() -> Unit

/**
 * Represents configured and running http server, capable of handling requests.
 * It is also the application coroutine scope that is cancelled immediately at application stop so useful
 * for launching background coroutines.
 */
@KtorDsl
public class HttpServer internal constructor(
    environment: ApplicationEnvironment,
    developmentMode: Boolean,
    public var rootPath: String,
    public val monitor: Events,
    public val parentCoroutineContext: CoroutineContext,
    private val engineProvider: () -> ApplicationEngine
) : ApplicationCallPipeline(developmentMode, environment), CoroutineScope {

    private val applicationJob = SupervisorJob(parentCoroutineContext[Job])

    public val engine: ApplicationEngine get() = engineProvider()

    override val coroutineContext: CoroutineContext = parentCoroutineContext + applicationJob

    /**
     * Called by [ApplicationEngine] when [HttpServer] is terminated.
     */
    @Suppress("DEPRECATION_ERROR")
    public fun dispose() {
        applicationJob.cancel()
        uninstallAllPlugins()
    }
}

/**
 * Convenience property to access log from application
 */
public val HttpServer.log: Logger get() = environment.log
