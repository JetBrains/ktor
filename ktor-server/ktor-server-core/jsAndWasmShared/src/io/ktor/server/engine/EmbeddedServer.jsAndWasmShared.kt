/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.events.EventDefinition
import io.ktor.server.application.*
import io.ktor.server.engine.internal.*
import kotlinx.coroutines.*

public actual class EmbeddedServer<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >
actual constructor(
    applicationProperties: ApplicationProperties,
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
    engineConfigBlock: TConfiguration.() -> Unit
) {
    public actual val monitor: Events = Events()

    public actual val environment: ApplicationEnvironment = applicationProperties.environment

    public actual val engineConfig: TConfiguration = engineFactory.configuration(engineConfigBlock)

    public actual val application: Application = Application(
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
        ::application
    )

    private val modules = applicationProperties.modules

    public actual fun start(wait: Boolean): EmbeddedServer<TEngine, TConfiguration> {
        safeRaiseEvent(ApplicationStarting, application)
        try {
            modules.forEach { application.it() }
        } catch (cause: Throwable) {
            environment.log.error("Failed to start application.", cause)
            destroy(application)
            throw cause
        }
        safeRaiseEvent(ApplicationStarted, application)

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

    public actual fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        engine.stop(gracePeriodMillis, timeoutMillis)
        destroy(application)
    }

    private fun destroy(application: Application) {
        safeRaiseEvent(ApplicationStopping, application)
        try {
            application.dispose()
        } catch (e: Throwable) {
            environment.log.error("Failed to destroy application instance.", e)
        }
        safeRaiseEvent(ApplicationStopped, application)
    }

    private fun safeRaiseEvent(event: EventDefinition<Application>, application: Application) {
        try {
            monitor.raise(event, application)
        } catch (cause: Throwable) {
            environment.log.error("One or more of the handlers thrown an exception", cause)
        }
    }
}
