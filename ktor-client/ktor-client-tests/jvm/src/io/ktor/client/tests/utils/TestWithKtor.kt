package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import ch.qos.logback.classic.Logger
import io.ktor.server.engine.*
import org.junit.*
import org.slf4j.*
import java.net.*
import java.util.concurrent.*

@Suppress("KDocMissingDocumentation")
abstract class TestWithKtor {
    protected val serverPort: Int = ServerSocket(0).use { it.localPort }

    abstract val server: ApplicationEngine

    init {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = Level.ERROR
    }

    @Before
    fun startServer() {
        server.start()
        ensureServerRunning()
    }

    @After
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
