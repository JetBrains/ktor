/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat.jakarta

import io.ktor.server.config.*
import io.ktor.server.engine.*

/**
 * Tomcat engine
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.tomcat.jakarta.EngineMain)
 */
public object EngineMain {
    /**
     * Main function for starting EngineMain with Tomcat
     * Creates an embedded Tomcat application with an environment built from command line arguments.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.tomcat.jakarta.EngineMain.main)
     */
    @JvmStatic
    public fun main(args: Array<String>) {
        val config = CommandLineConfig(args)
        val server = EmbeddedServer(config.rootConfig, Tomcat) {
            takeFrom(config.engineConfig)
            loadConfiguration(config.rootConfig.environment.config)
        }
        server.start(true)
    }

    private fun TomcatApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
    }
}
