/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

expect abstract class EngineTestBase<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
) : BaseTest, CoroutineScope {

    override val coroutineContext: CoroutineContext

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected annotation class Http2Only()

    val applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>

    protected var enableHttp2: Boolean
    protected var enableSsl: Boolean
    protected var enableCertVerify: Boolean

    protected var port: Int
    protected var sslPort: Int
    protected var server: EmbeddedServer<TEngine, TConfiguration>?

    protected suspend fun startServer(server: EmbeddedServer<TEngine, TConfiguration>): List<Throwable>

    protected suspend fun createAndStartServer(
        log: Logger? = null,
        parent: CoroutineContext = EmptyCoroutineContext,
        routingConfigurer: Route.() -> Unit
    ): EmbeddedServer<TEngine, TConfiguration>

    protected open fun plugins(application: Application, routingConfig: Route.() -> Unit)

    protected suspend fun withUrl(
        path: String,
        builder: suspend HttpRequestBuilder.() -> Unit = {},
        block: suspend HttpResponse.(Int) -> Unit
    )
}
