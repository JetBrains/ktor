/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.response.*
import io.ktor.server.servlet.jakarta.*
import org.slf4j.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.test.*

@Suppress("BlockingMethodInNonBlockingContext")
class MultipleDispatchOnTimeout {

    private fun findFreePort() = ServerSocket(0).use { it.localPort }

    /**
     * We are testing that the servlet container does not trigger an extra error dispatch for calls that timeout from
     * the perspective of the servlet container. The fact that it does so is apparently specified here on this url:
     * https://docs.oracle.com/javaee/6/api/javax/servlet/AsyncContext.html
     */
    @Test
    fun `calls with duration longer than default timeout do not trigger a redispatch`() {
        val callCount = AtomicInteger(0)
        val port = findFreePort()
        val environment = applicationEnvironment {
            log = LoggerFactory.getLogger("ktor.test")
        }
        val props = applicationProperties(environment) {
            module {
                intercept(ApplicationCallPipeline.Call) {
                    callCount.incrementAndGet()
                    val timeout = Math.max(
                        (call.request as ServletApplicationRequest).servletRequest.asyncContext.timeout,
                        0
                    )
                    Thread.sleep(timeout + 1000)
                    call.respondTextWriter {
                        write("A ok!")
                    }
                }
            }
        }

        val jetty = embeddedServer(Jetty, props) {
            connector { this.port = port }
        }
        try {
            jetty.start()

            Thread.sleep(1000)

            val result = URL("http://localhost:$port/").openConnection().inputStream.bufferedReader().readLine().let {
                it
            } ?: "<empty>"

            // println("Got result: $result" )

            assertEquals(1, callCount.get())
            assertEquals("A ok!", result)
        } finally {
            jetty.stop(1, 5, TimeUnit.SECONDS)
        }
    }
}
