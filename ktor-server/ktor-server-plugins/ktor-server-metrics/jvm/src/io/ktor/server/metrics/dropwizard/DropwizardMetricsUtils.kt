/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.dropwizard

import com.codahale.metrics.*
import io.ktor.util.*

internal class RoutingMetrics(val name: String, val context: Timer.Context)
internal val routingMetricsKey = AttributeKey<RoutingMetrics>("dropwizardRoutingMetrics")

internal data class CallMeasure(val timer: Timer.Context)
internal val measureKey = AttributeKey<CallMeasure>("dropwizardMetrics")
