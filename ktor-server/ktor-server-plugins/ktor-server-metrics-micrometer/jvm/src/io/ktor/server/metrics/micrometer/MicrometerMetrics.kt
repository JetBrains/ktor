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
import io.ktor.utils.io.*
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
 * A configuration for the [MicrometerMetrics] plugin.
 */
@KtorDsl
public class MicrometerMetricsConfig {
    /**
     * Specifies the base name (prefix) of Ktor metrics used for monitoring HTTP requests.
     * For example, the default "ktor.http.server.requests" values results in the following metrics:
     * - "ktor.http.server.requests.active"
     * - "ktor.http.server.requests.seconds.max"
     *
     * If you change it to "custom.metric.name", the mentioned metrics will look as follows:
     * - "custom.metric.name.active"
     * - "custom.metric.name.seconds.max"
     * @see [MicrometerMetrics]
     */
    public var metricName: String = "ktor.http.server.requests"

    /**
     * Specifies the meter registry for your monitoring system.
     * The example below shows how to create the `PrometheusMeterRegistry`:
     * ```kotlin
     * install(MicrometerMetrics) {
     *     registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
     * }
     * ```
     * @see [MicrometerMetrics]
     */
    public var registry: MeterRegistry = LoggingMeterRegistry()
        set(value) {
            field.close()
            field = value
        }

    /**
     * Specifies if requests for non-existent routes should
     * contain a request path or fallback to common `n/a` value. `true` by default.
     * @see [MicrometerMetrics]
     */
    public var distinctNotRegisteredRoutes: Boolean = true

    /**
     * Allows you to configure a set of metrics for monitoring the JVM.
     * To disable these metrics, assign an empty list to [meterBinders]:
     * ```kotlin
     * meterBinders = emptyList()
     * ```
     * @see [MicrometerMetrics]
     */
    public var meterBinders: List<MeterBinder> = listOf(
        ClassLoaderMetrics(),
        JvmMemoryMetrics(),
        JvmGcMetrics(),
        ProcessorMetrics(),
        JvmThreadMetrics(),
        FileDescriptorMetrics()
    )

    /**
     * Configures the histogram and/or percentiles for all request timers.
     * By default, 50%, 90% , 95% and 99% percentiles are configured.
     * If your backend supports server side histograms, you should enable these instead
     * with [DistributionStatisticConfig.Builder.percentilesHistogram] as client side percentiles cannot be aggregated.
     * @see [MicrometerMetrics]
     */
    public var distributionStatisticConfig: DistributionStatisticConfig =
        DistributionStatisticConfig.Builder().percentiles(0.5, 0.9, 0.95, 0.99).build()

    internal var timerBuilder: Timer.Builder.(ApplicationCall, Throwable?) -> Unit = { _, _ -> }

    /**
     * Configures micrometer timers.
     * Can be used to customize tags for each timer, configure individual SLAs, and so on.
     */
    public fun timers(block: Timer.Builder.(ApplicationCall, Throwable?) -> Unit) {
        timerBuilder = block
    }
}

/**
 * A plugin that enables Micrometer metrics in your Ktor server application and
 * allows you to choose the required monitoring system, such as Prometheus, JMX, Elastic, and so on.
 * By default, Ktor exposes metrics for monitoring HTTP requests and a set of low-level metrics for monitoring the JVM.
 * You can customize these metrics or create new ones.
 *
 * You can learn more from [Micrometer metrics](https://ktor.io/docs/micrometer-metrics.html).
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
        val measureKey = AttributeKey<CallMeasure>("micrometerMetrics")

        fun Timer.Builder.addDefaultTags(call: ApplicationCall, throwable: Throwable?): Timer.Builder {
            val route = call.attributes[measureKey].route
                ?: if (pluginConfig.distinctNotRegisteredRoutes) call.request.path() else "n/a"
            tags(
                listOf(
                    of("address", call.request.local.let { "${it.localHost}:${it.localPort}" }),
                    of("method", call.request.httpMethod.value),
                    of("route", route),
                    of("status", call.response.status()?.value?.toString() ?: "n/a"),
                    of("throwable", throwable?.let { it::class.qualifiedName } ?: "n/a")
                )
            )
            return this
        }

        registry.config().meterFilter(object : MeterFilter {
            override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig =
                if (id.name == metricName) pluginConfig.distributionStatisticConfig.merge(config) else config
        })
        pluginConfig.meterBinders.forEach { it.bindTo(pluginConfig.registry) }

        @OptIn(InternalAPI::class)
        on(Metrics) { call ->
            active?.incrementAndGet()
            call.attributes.put(measureKey, CallMeasure(Timer.start(registry)))
        }

        on(ResponseSent) { call ->
            active?.decrementAndGet()
            val measure = call.attributes[measureKey]
            measure.timer.stop(
                Timer.builder(metricName)
                    .addDefaultTags(call, measure.throwable)
                    .apply { pluginConfig.timerBuilder(this, call, measure.throwable) }
                    .register(registry)
            )
        }

        on(CallFailed) { call, cause ->
            call.attributes.getOrNull(measureKey)?.throwable = cause
            throw cause
        }

        application.monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
            call.attributes[measureKey].route = call.route.parent.toString()
        }
    }

private data class CallMeasure(
    val timer: Timer.Sample,
    var route: String? = null,
    var throwable: Throwable? = null
)
