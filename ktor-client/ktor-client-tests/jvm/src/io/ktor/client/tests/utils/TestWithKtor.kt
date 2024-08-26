/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import ch.qos.logback.classic.Logger
import io.ktor.server.engine.*
import kotlinx.coroutines.debug.junit5.*
import org.junit.jupiter.api.*
import org.slf4j.*
import java.net.*
import java.util.concurrent.*

@CoroutinesTimeout(5 * 60 * 1000)
abstract class TestWithKtor {
    protected val serverPort: Int = ServerSocket(0).use { it.localPort }
    protected val testUrl: String = "http://localhost:$serverPort"

    abstract val server: EmbeddedServer<*, *>

    init {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = Level.ERROR
    }

    @BeforeEach
    fun startServer() {
        var attempt = 0

        do {
            attempt++
            try {
                server.start(wait = false)
                break
            } catch (cause: Throwable) {
                if (attempt >= 10) throw cause
                Thread.sleep(250L * attempt)
            }
        } while (true)

        ensureServerRunning()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0, 0, TimeUnit.SECONDS)
    }

    private fun ensureServerRunning() {
        do {
            try {
                Socket("localhost", serverPort).close()
                break
            } catch (_: Throwable) {
                Thread.sleep(100)
            }
        } while (true)
    }
}
