package io.ktor.metrics.micrometer

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.binder.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import io.micrometer.core.instrument.simple.*
import org.junit.Test
import kotlin.reflect.*
import kotlin.test.*

class MicrometerMetricsTests {

    @Test
    fun `time is measured for requests`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        application.routing {
            get("/uri") {
                call.respond("hello")
            }
        }
        doOneRequest("/uri")

        val timers = testRegistry.find(MicrometerMetrics.requestTimerName).timers()
        assertEquals(1, timers.size)
        timers.first().run {
            assertTag("status", "200")
            assertTag("route", "/uri")
            assertTag("method", "GET")
            assertTag("local", "localhost:80")
        }
    }

    @Test
    fun `errors are recorded`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        application.routing {
            get("/uri") {
                throw IllegalAccessException("something went wrong")
            }
        }
        try {
            doOneRequest("/uri")
        } catch (e: Exception) {
            // not interesting for this test
        }
        with(testRegistry.find(MicrometerMetrics.requestTimerName).timers()) {
            assertEquals(1, size)
            this.first().run {
                assertTag("status", "java.lang.IllegalAccessException")
                assertTag("route", "/uri")
                assertTag("method", "GET")
                assertTag("local", "localhost:80")
            }
        }
    }

    @Test
    fun `parameter names are recorded instead of values`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        application.routing {
            get("/uri/{someParameter}") {
                call.respond("some response")
            }
        }

        doOneRequest("/uri/someParameterValue")

        with(testRegistry.find(MicrometerMetrics.requestTimerName).timers()) {
            assertEquals(1, size)
            this.first().run {
                assertTag("status", "200")
                assertTag("route", "/uri/{someParameter}")
                assertTag("method", "GET")
                assertTag("local", "localhost:80")
            }
        }
    }

    @Test
    fun `Class loader metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(ClassLoaderMetrics::class, "jvm.classes.loaded", "jvm.classes.unloaded")
    }

    @Test
    fun `Memory metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(
            ClassLoaderMetrics::class,
            "jvm.memory.used",
            "jvm.memory.committed",
            "jvm.memory.max"
        )
    }

    @Test
    fun `Garbage Collection metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(
            JvmGcMetrics::class,
            "jvm.gc.max.data.size",
            "jvm.gc.live.data.size",
            "jvm.gc.memory.promoted",
            "jvm.gc.memory.allocated"
        )
    }

    @Test
    fun `Processor metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(
            ProcessorMetrics::class,
            "system.cpu.count"
        )
    }

    @Test
    fun `Thread metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(
            JvmThreadMetrics::class,
            "jvm.threads.peak", "jvm.threads.daemon", "jvm.threads.live", "jvm.threads.states"
        )
    }


    private fun TestApplicationEngine.doOneRequest(path: String = "/uri") {
        handleRequest {
            uri = path
            method = HttpMethod.Get
        }
    }


    private fun TestApplicationEngine.metersAreRegistered(
        meterBinder: KClass<out MeterBinder>,
        vararg meterNames: String
    ) {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        meterNames.forEach { testRegistry.shouldHaveMetricFrom(meterBinder, it) }
    }


    private fun MeterRegistry.shouldHaveMetricFrom(
        meterRegistry: KClass<out MeterBinder>,
        meterName: String
    ) {
        assertNotEquals(
            listOf(),
            this.find(meterName).meters(),
            "should have a metrics from ${meterRegistry.qualifiedName}"
        )
    }

    private fun Meter.assertTag(tagName: String, expectedValue: String) {
        val tag = this.id.tags.find { it.key == tagName }

        assertNotNull(tag, "$this does not contain a tag named '$tagName'")
        assertEquals(expectedValue, tag.value, "Tag value for '$tagName' should be '$expectedValue'")
    }
}



