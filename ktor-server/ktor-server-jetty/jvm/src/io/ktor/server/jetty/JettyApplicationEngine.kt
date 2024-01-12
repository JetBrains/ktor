/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*

/**
 * [ApplicationEngine] implementation for running in a standalone Jetty
 */
public class JettyApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    configuration: Configuration,
    private val applicationProvider: () -> Application
) : JettyApplicationEngineBase(environment, monitor, developmentMode, configuration, applicationProvider) {

    private val dispatcher = server.threadPool.asCoroutineDispatcher()

    override fun start(wait: Boolean): JettyApplicationEngine {
        server.handler = JettyKtorHandler(environment, pipeline, dispatcher, configuration, applicationProvider)
        super.start(wait)
        return this
    }
}
