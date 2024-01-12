/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.engine.*
import io.ktor.util.*
import jakarta.servlet.annotation.*
import org.slf4j.*
import kotlin.coroutines.*

/**
 * This servlet need to be installed into a servlet container
 */
@MultipartConfig
public open class ServletApplicationEngine : KtorServlet() {

    private val embeddedServer: EmbeddedServer<ApplicationEngine, ApplicationEngine.Configuration>? by lazy {
        servletContext.getAttribute(ApplicationAttributeKey)?.let {
            return@lazy null
        }

        val servletContext = servletContext
        val servletConfig = servletConfig

        val parameterNames = (
            servletContext.initParameterNames?.toList().orEmpty() +
                servletConfig.initParameterNames?.toList().orEmpty()
            ).filter { it.startsWith("io.ktor") }.distinct()
        val parameters = parameterNames.map {
            it.removePrefix("io.ktor.") to
                (servletConfig.getInitParameter(it) ?: servletContext.getInitParameter(it))
        }

        val parametersConfig = MapApplicationConfig(parameters)
        val configPath = "ktor.config"
        val applicationIdPath = "ktor.application.id"

        val combinedConfig = parametersConfig
            .withFallback(ConfigLoader.load(parametersConfig.tryGetString(configPath)))

        val applicationId = combinedConfig.tryGetString(applicationIdPath) ?: "Application"

        val environment = applicationEnvironment {
            config = combinedConfig
            log = LoggerFactory.getLogger(applicationId)
            classLoader = servletContext.classLoader
        }
        val applicationProperties = applicationProperties(environment) {
            rootPath = servletContext.contextPath ?: "/"
        }
        val server = EmbeddedServer(applicationProperties, EmptyEngineFactory)
        server.apply {
            monitor.subscribe(ApplicationStarting) {
                it.receivePipeline.merge(enginePipeline.receivePipeline)
                it.sendPipeline.merge(enginePipeline.sendPipeline)
                it.receivePipeline.installDefaultTransformations()
                it.sendPipeline.installDefaultTransformations()
            }
        }
    }

    public val environment: ApplicationEnvironment
        get() = servletContext.getAttribute(EnvironmentAttributeKey)?.let { it as ApplicationEnvironment }
            ?: embeddedServer!!.environment

    @Suppress("UNCHECKED_CAST")
    override val application: Application
        get() = servletContext.getAttribute(ApplicationAttributeKey)?.let { it as () -> Application }?.invoke()
            ?: embeddedServer!!.application

    override val logger: Logger get() = environment.log

    override val enginePipeline: EnginePipeline by lazy {
        servletContext.getAttribute(ApplicationEnginePipelineAttributeKey)?.let { return@lazy it as EnginePipeline }

        defaultEnginePipeline(environment.config, application.developmentMode).also {
            BaseApplicationResponse.setupSendPipeline(it.sendPipeline)
        }
    }

    override val upgrade: ServletUpgrade by lazy {
        if ("jetty" in (servletContext.serverInfo?.toLowerCasePreservingASCIIRules() ?: "")) {
            jettyUpgrade ?: DefaultServletUpgrade
        } else {
            DefaultServletUpgrade
        }
    }

    override val coroutineContext: CoroutineContext
        get() = super.coroutineContext + application.parentCoroutineContext

    /**
     * Called by the servlet container when loading the servlet (on load)
     */
    override fun init() {
        embeddedServer?.start()
        super.init()
    }

    override fun destroy() {
        application.monitor.raise(ApplicationStopPreparing, environment)
        super.destroy()
        embeddedServer?.stop()
    }

    public companion object {
        /**
         * An embedded server instance key. It is not recommended to use unless you are writing
         * your own servlet application engine implementation
         */
        public const val EnvironmentAttributeKey: String = "_ktor_environment_instance"
        public const val ApplicationAttributeKey: String = "_ktor_application_instance"

        /**
         * An application engine pipeline instance key. It is not recommended to use unless you are writing
         * your own servlet application engine implementation
         */
        public const val ApplicationEnginePipelineAttributeKey: String = "_ktor_application_engine_pipeline_instance"

        private val jettyUpgrade by lazy {
            try {
                Class.forName("io.ktor.server.jetty.jakarta.internal.JettyUpgradeImpl").kotlin.objectInstance
                    as ServletUpgrade
            } catch (t: Throwable) {
                null
            }
        }
    }
}

private object EmptyEngineFactory : ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration> {
    override fun configuration(
        configure: ApplicationEngine.Configuration.() -> Unit
    ): ApplicationEngine.Configuration {
        return ApplicationEngine.Configuration()
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): ApplicationEngine {
        return object : ApplicationEngine {
            override val environment: ApplicationEnvironment = environment
            override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()
            override fun start(wait: Boolean): ApplicationEngine = this
            override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) = Unit
        }
    }
}
