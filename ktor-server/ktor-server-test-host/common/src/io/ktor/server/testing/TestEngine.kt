/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.util.logging.*

/**
 * Creates an engine environment for a test application.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.testing.createTestEnvironment)
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
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated(
    "Use new `testApplication` API: https://ktor.io/docs/migration-to-20x.html#testing-api",
    level = DeprecationLevel.ERROR,
)
public fun <R> withApplication(
    environment: ApplicationEnvironment = createTestEnvironment(),
    configure: TestApplicationEngine.Configuration.() -> Unit = {},
    test: TestApplicationEngine.() -> R
): R {
    val properties = serverConfig(environment) {
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
@Deprecated(
    "Use new `testApplication` API: https://ktor.io/docs/migration-to-20x.html#testing-api",
    level = DeprecationLevel.ERROR,
)
public fun <R> withTestApplication(test: TestApplicationEngine.() -> R): R {
    @Suppress("DEPRECATION_ERROR")
    return withApplication(createTestEnvironment(), test = test)
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated(
    "Use new `testApplication` API: https://ktor.io/docs/migration-to-20x.html#testing-api",
    level = DeprecationLevel.ERROR,
)
public fun <R> withTestApplication(moduleFunction: Application.() -> Unit, test: TestApplicationEngine.() -> R): R {
    @Suppress("DEPRECATION_ERROR")
    return withApplication(createTestEnvironment()) {
        moduleFunction(application)
        test()
    }
}

/**
 * Starts a test application engine, passes it to the [test] function, and stops it.
 */
@Deprecated(
    "Use new `testApplication` API: https://ktor.io/docs/migration-to-20x.html#testing-api",
    level = DeprecationLevel.ERROR,
)
public fun <R> withTestApplication(
    moduleFunction: Application.() -> Unit,
    configure: TestApplicationEngine.Configuration.() -> Unit = {},
    test: TestApplicationEngine.() -> R
): R {
    @Suppress("DEPRECATION_ERROR")
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
