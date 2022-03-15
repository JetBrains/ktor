/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.micrometer

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.application.hooks.Metrics
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.Tag.*
import io.micrometer.core.instrument.binder.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import io.micrometer.core.instrument.config.*
import io.micrometer.core.instrument.distribution.*
import io.micrometer.core.instrument.logging.*
import java.util.concurrent.atomic.*

/**
 * Configures [MicrometerMetrics] Plugin
 * @property metricName The name for metrics.
 * @property registry The meter registry where the meters are registered. Mandatory
 * @property meterBinders The binders that are automatically bound to the registry.
 * Default: [ClassLoaderMetrics],
 * [JvmMemoryMetrics], [ProcessorMetrics], [JvmGcMetrics],
 * [ProcessorMetrics], [JvmThreadMetrics], [FileDescriptorMetrics]
 * @property distributionStatisticConfig configures the histogram and/or percentiles for all request timers.
 * By default, 50%, 90% , 95% and 99% percentiles are configured. If your backend supports server side histograms you
 * should enable these instead with [DistributionStatisticConfig.Builder.percentilesHistogram] as client side
 * percentiles cannot be aggregated.
 * @property timers can be used to configure each timer to add custom tags or configure individual SLAs etc
 * @property distinctNotRegisteredRoutes specifies if requests for non existent routes should
 * contain request path or fallback to common `n/a` value. `true` by default
 * */
@KtorDsl
public class MicrometerMetricsConfig {

    public companion object {
        public val measureKey: AttributeKey<CallMeasure> = AttributeKey("metrics")
    }

    public var metricName: String = "ktor.http.server.requests"

    public var registry: MeterRegistry = LoggingMeterRegistry()

    public var distinctNotRegisteredRoutes: Boolean = true

    public var meterBinders: List<MeterBinder> = listOf(
        ClassLoaderMetrics(),
        JvmMemoryMetrics(),
        JvmGcMetrics(),
        ProcessorMetrics(),
        JvmThreadMetrics(),
        FileDescriptorMetrics()
    )

    public var distributionStatisticConfig: DistributionStatisticConfig =
        DistributionStatisticConfig.Builder().percentiles(0.5, 0.9, 0.95, 0.99).build()

    internal var timerBuilder: Timer.Builder.(ApplicationCall, Throwable?) -> Unit = { _, _ -> }
    internal var defaultTags: Timer.Builder.(ApplicationCall, Throwable?) -> Timer.Builder = { call, throwable ->
        val route = call.attributes[measureKey].route
            ?: if (distinctNotRegisteredRoutes) call.request.path() else "n/a"
        tags(
            listOf(
                of("address", call.request.local.let { "${it.host}:${it.port}" }),
                of("method", call.request.httpMethod.value),
                of("route", route),
                of("status", call.response.status()?.value?.toString() ?: "n/a"),
                of("throwable", throwable?.let { it::class.qualifiedName } ?: "n/a")
            )
        )
        this
    }

    /**
     * Configure micrometer timers
     */
    public fun timers(block: Timer.Builder.(ApplicationCall, Throwable?) -> Unit) {
        timerBuilder = block
    }
}

/**
 * Enables Micrometer support when installed.
 * Exposes the following metrics:
 * * 'ktor.http.server.requests.active': Gauge - The amount of active ktor requests
 * * 'ktor.http.server.requests': Timer - Timer for all requests.
 *     By default, no percentiles or histogram is exposed.
 *     Use the [Configuration.distributionStatisticConfig] to enable these.
 *     Tags by default (use [Configuration.tags] to configure the tags or add custom tags):
 * 1. 'address': The host and port of the request uri (e.g. 'www.ktor.io:443' from the uri 'https://www.ktor.io/foo/bar')
 * 2. 'method': The http method (e.g. 'GET')
 * 3. 'route': The use ktor route used for this request. (e.g. '/some/path/{someParameter}')
 * 4. 'status': The http status code that was set in the response
 *   or 404 if no handler was found for this request or 500 if an exception was thrown
 * 5. 'throwable': The class name of the throwable that was eventually thrown while processing
 *   the request (or 'n/a' if no throwable had been thrown).
 *   Please note, that if an exception is thrown after calling [ApplicationCall.respond()], the tag is still "n/a"
 */
public val MicrometerMetrics: ApplicationPlugin<MicrometerMetricsConfig> =
    createApplicationPlugin("MicrometerMetrics", ::MicrometerMetricsConfig) {

        if (pluginConfig.metricName.isBlank()) {
            throw IllegalArgumentException("Metric name should be defined")
        }

        val metricName = pluginConfig.metricName
        val activeRequestsGaugeName = "$metricName.active"
        val registry = pluginConfig.registry
        val active = registry.gauge(activeRequestsGaugeName, AtomicInteger(0))

        registry.config().meterFilter(object : MeterFilter {
            override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig =
                if (id.name == metricName) pluginConfig.distributionStatisticConfig.merge(config) else config
        })
        pluginConfig.meterBinders.forEach { it.bindTo(pluginConfig.registry) }

        @OptIn(InternalAPI::class)
        on(Metrics) { call ->
            active?.incrementAndGet()
            call.attributes.put(MicrometerMetricsConfig.measureKey, CallMeasure(Timer.start(registry)))
        }

        on(ResponseSent) { call ->
            active?.decrementAndGet()
            val defaultTags = pluginConfig.defaultTags
            val measure = call.attributes[MicrometerMetricsConfig.measureKey]
            measure.timer.stop(
                Timer.builder(metricName)
                    .defaultTags(call, measure.throwable)
                    .apply { pluginConfig.timerBuilder(this, call, measure.throwable) }
                    .register(registry)
            )
        }

        on(CallFailed) { call, cause ->
            call.attributes.getOrNull(MicrometerMetricsConfig.measureKey)?.throwable = cause
            throw cause
        }

        environment!!.monitor.subscribe(Routing.RoutingCallStarted) { call ->
            call.attributes[MicrometerMetricsConfig.measureKey].route = call.route.parent.toString()
        }
    }

public data class CallMeasure(
    val timer: Timer.Sample,
    var route: String? = null,
    var throwable: Throwable? = null
)
