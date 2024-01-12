/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.server.testing

import io.ktor.events.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.util.logging.*

internal expect fun DefaultTestConfig(configPath: String? = null): ApplicationConfig

/**
 * Creates an engine environment for a test application.
 */
public fun createTestEnvironment(
    configure: ApplicationEnvironmentBuilder.() -> Unit = {}
): ApplicationEnvironment =
    applicationEnvironment {
        config = MapApplicationConfig("ktor.deployment.environment" to "test")
        log = KtorSimpleLogger("io.ktor.test")
        configure()
    }

/**
 * Makes a test request.
 */
public fun TestApplicationEngine.handleRequest(
    method: HttpMethod,
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall = handleRequest {
    this.uri = uri
    this.method = method
    setup()
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated("Please use new `testApplication` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withApplication(
    environment: ApplicationEnvironment = createTestEnvironment(),
    configure: TestApplicationEngine.Configuration.() -> Unit = {},
    test: TestApplicationEngine.() -> R
): R {
    val properties = applicationProperties(environment) {
        watchPaths = emptyList()
    }
    val embeddedServer = EmbeddedServer(properties, TestEngine, configure)
    embeddedServer.start()
    try {
        return embeddedServer.engine.test()
    } finally {
        embeddedServer.stop()
    }
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated("Please use new `testApplication` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(test: TestApplicationEngine.() -> R): R {
    return withApplication(createTestEnvironment(), test = test)
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated("Please use new `testApplication` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(moduleFunction: Application.() -> Unit, test: TestApplicationEngine.() -> R): R {
    return withApplication(createTestEnvironment()) {
        moduleFunction(application)
        test()
    }
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated("Please use new `testApplication` API: https://ktor.io/docs/migrating-2.html#testing-api")
public fun <R> withTestApplication(
    moduleFunction: Application.() -> Unit,
    configure: TestApplicationEngine.Configuration.() -> Unit = {},
    test: TestApplicationEngine.() -> R
): R {
    return withApplication(createTestEnvironment(), configure) {
        moduleFunction(application)
        test()
    }
}

/**
 * An [ApplicationEngineFactory] providing a CIO-based [ApplicationEngine].
 */
public object TestEngine : ApplicationEngineFactory<TestApplicationEngine, TestApplicationEngine.Configuration> {

    override fun configuration(
        configure: TestApplicationEngine.Configuration.() -> Unit
    ): TestApplicationEngine.Configuration {
        return TestApplicationEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: TestApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): TestApplicationEngine {
        return TestApplicationEngine(environment, monitor, developmentMode, applicationProvider, configuration)
    }
}
