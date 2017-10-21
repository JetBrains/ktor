package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import ch.qos.logback.classic.Logger
import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.server.host.*
import org.junit.*
import org.slf4j.*
import java.util.concurrent.*

abstract class TestWithKtor(private val backendFactory: HttpClientBackendFactory) {
    abstract val server: ApplicationHost

    init {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = Level.ERROR
    }

    fun createClient() = HttpClient(backendFactory)

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0, 0, TimeUnit.SECONDS)
    }
}
