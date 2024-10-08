/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import org.eclipse.jetty.server.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * [ApplicationEngine] base type for running in a standalone Jetty
 */
public open class JettyApplicationEngineBase(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    /**
     * Application engine configuration specifying engine-specific options such as parallelism level.
     */
    public val configuration: Configuration,
    private val applicationProvider: () -> Application
) : BaseApplicationEngine(environment, monitor, developmentMode) {

    /**
     * Jetty-specific engine configuration
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Property function that will be called during Jetty server initialization
         * with the server instance as receiver.
         */
        public var configureServer: Server.() -> Unit = {}

        /**
         * Property function that will be called during Jetty server initialization with the http configuration instance
         * that is passed to the managed connectors as a receiver.
         */
        public var httpConfiguration: HttpConfiguration.() -> Unit = {}

        /**
         * The duration of time that a connection can be idle before the connector takes action to close the connection.
         */
        public var idleTimeout: Duration = 30.seconds
    }

    private var cancellationDeferred: CompletableJob? = null

    /**
     * Jetty server instance being configuring and starting
     */
    protected val server: Server = Server().apply {
        initializeServer(configuration)
        configuration.configureServer(this)
    }

    override fun start(wait: Boolean): JettyApplicationEngineBase {
        server.start()
        cancellationDeferred = stopServerOnCancellation(
            applicationProvider(),
            configuration.shutdownGracePeriod,
            configuration.shutdownTimeout
        )

        val connectors = server.connectors.zip(configuration.connectors)
            .map { it.second.withPort((it.first as ServerConnector).localPort) }
        resolvedConnectorsDeferred.complete(connectors)

        monitor.raiseCatching(ServerReady, environment, environment.log)

        if (wait) {
            server.join()
            stop(configuration.shutdownGracePeriod, configuration.shutdownTimeout)
        }
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        cancellationDeferred?.complete()
        monitor.raise(ApplicationStopPreparing, environment)
        server.stopTimeout = timeoutMillis
        server.stop()
        server.destroy()
    }

    override fun toString(): String {
        return "Jetty($environment)"
    }
}
