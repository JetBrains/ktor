/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.engine.internal.*

/**
 * An engine which runs an application.
 */
public interface ApplicationEngine {

    /**
     * A configuration for the [ApplicationEngine].
     */
    @Suppress("MemberVisibilityCanBePrivate")
    public open class Configuration {
        /**
         * Returns the current parallelism level (e.g. the number of available processors).
         */
        public val parallelism: Int = availableProcessorsBridge()

        /**
         * Specifies how many threads are used to accept new connections and start call processing.
         */
        public var connectionGroupSize: Int = parallelism / 2 + 1

        /**
         * Specifies size of the event group for processing connections, parsing messages and doing engine's internal work
         */
        public var workerGroupSize: Int = parallelism / 2 + 1

        /**
         * Specifies the minimum size of a thread pool used to process application calls.
         */
        public var callGroupSize: Int = parallelism

        /**
         * Specifies the maximum amount of time in milliseconds for activity to cool down
         */
        public var shutdownGracePeriod: Long = 1000

        /**
         * Specifies the maximum amount of time in milliseconds to wait until server stops gracefully
         */
        public var shutdownTimeout: Long = 5000

        /**
         * List of connectors describing where and how the server should listen.
         */
        public var connectors: MutableList<EngineConnectorConfig> = mutableListOf()

        /**
         * Uses [other] configuration and overrides this with its values.
         */
        public fun takeFrom(other: Configuration) {
            connectionGroupSize = other.connectionGroupSize
            workerGroupSize = other.workerGroupSize
            callGroupSize = other.callGroupSize
            shutdownGracePeriod = other.shutdownGracePeriod
            shutdownTimeout = other.shutdownTimeout
            connectors.addAll(other.connectors)
        }
    }

    /**
     * Local addresses for application connectors.
     * If [environment]'s [connector]s was configured to use port=0, you can use this function to get an actual port
     * for these connectors.
     * Available after a server is started.
     */
    public suspend fun resolvedConnectors(): List<EngineConnectorConfig>

    /**
     * An environment used to run this engine.
     */
    public val environment: ApplicationEnvironment

    /**
     * Starts this [ApplicationEngine].
     *
     * @param wait if true, then the `start` call blocks a current thread until it finishes its execution.
     * If you run `start` from the main thread with `wait = false` and nothing else blocking this thread,
     * then your application will be terminated without handling any requests.
     * @return returns this instance
     */
    public fun start(wait: Boolean = false): ApplicationEngine

    /**
     * Stops this [ApplicationEngine].
     *
     * @param gracePeriodMillis the maximum amount of time for activity to cool down
     * @param timeoutMillis the maximum amount of time to wait until a server stops gracefully
     */
    public fun stop(gracePeriodMillis: Long = 500, timeoutMillis: Long = 500)
}
