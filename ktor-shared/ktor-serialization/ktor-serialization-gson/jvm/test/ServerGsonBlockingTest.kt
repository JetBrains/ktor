/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import java.io.*
import java.lang.reflect.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.test.*

class ServerGsonBlockingTest {
    private val dispatcher = UnsafeDispatcher()

    @AfterTest
    fun cleanup() {
        dispatcher.close()
    }

    @Test
    fun testReceive() = testApplication {
        testApplicationProperties {
            parentCoroutineContext = dispatcher
        }
        application {
            intercept(ApplicationCallPipeline.Setup) {
                withContext(dispatcher) {
                    proceed()
                }
            }
        }
        install(ContentNegotiation) {
            gson()
        }
        routing {
            post("/") {
                assertEquals(K(77), call.receive())
                call.respondText("OK")
            }
        }

        runBlocking {
            assertEquals(
                "OK",
                client.post("/") {
                    setBody("{\"i\": 77}")
                    contentType(ContentType.Application.Json)
                }.body()
            )
        }
    }

    data class K(var i: Int)

    private class UnsafeDispatcher : CoroutineDispatcher(), Closeable {
        private val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatcher.dispatch(context + dispatcher) {
                markParkingProhibited()
                block.run()
            }
        }

        override fun close() {
            dispatcher.close()
        }

        private val prohibitParkingFunction: Method? by lazy {
            try {
                Class.forName("io.ktor.utils.io.jvm.javaio.PollersKt")
                    .getMethod("prohibitParking")
            } catch (cause: Throwable) {
                null
            }
        }

        private fun markParkingProhibited() {
            try {
                prohibitParkingFunction?.invoke(null)
            } catch (cause: Throwable) {
            }
        }
    }
}
