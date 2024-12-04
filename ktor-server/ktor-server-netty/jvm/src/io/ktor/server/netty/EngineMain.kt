/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.server.config.*
import io.ktor.server.engine.*

/**
 * Netty engine
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.netty.EngineMain)
 */
public object EngineMain {
    /**
     * Main function for starting EngineMain with Netty
     * Creates an embedded Netty application with an environment built from command line arguments.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.netty.EngineMain.main)
     */
    @JvmStatic
    public fun main(args: Array<String>) {
        val config = CommandLineConfig(args)
        val server = EmbeddedServer(config.rootConfig, Netty) {
            takeFrom(config.engineConfig)
            loadConfiguration(config.rootConfig.environment.config)
        }
        server.start(true)
    }

    internal fun NettyApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
        deploymentConfig.propertyOrNull("runningLimit")?.getString()?.toInt()?.let {
            runningLimit = it
        }
        deploymentConfig.propertyOrNull("shareWorkGroup")?.getString()?.toBoolean()?.let {
            shareWorkGroup = it
        }
        deploymentConfig.propertyOrNull("responseWriteTimeoutSeconds")?.getString()?.toInt()?.let {
            responseWriteTimeoutSeconds = it
        }
        deploymentConfig.propertyOrNull("requestReadTimeoutSeconds")?.getString()?.toInt()?.let {
            requestReadTimeoutSeconds = it
        }
        deploymentConfig.propertyOrNull("tcpKeepAlive")?.getString()?.toBoolean()?.let {
            tcpKeepAlive = it
        }
        deploymentConfig.propertyOrNull("maxInitialLineLength")?.getString()?.toInt()?.let {
            maxInitialLineLength = it
        }
        deploymentConfig.propertyOrNull("maxHeaderSize")?.getString()?.toInt()?.let {
            maxHeaderSize = it
        }
        deploymentConfig.propertyOrNull("maxChunkSize")?.getString()?.toInt()?.let {
            maxChunkSize = it
        }
    }
}
