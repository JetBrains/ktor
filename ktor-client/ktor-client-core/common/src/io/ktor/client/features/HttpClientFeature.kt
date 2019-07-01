/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.util.*
import kotlin.native.concurrent.*

@SharedImmutable
internal val FEATURE_INSTALLED_LIST = AttributeKey<Attributes>("ApplicationFeatureRegistry")

/**
 * Base interface representing a [HttpClient] feature.
 */
interface HttpClientFeature<out TConfig : Any, TFeature : Any> {
    /**
     * The [AttributeKey] for this feature.
     */
    val key: AttributeKey<TFeature>

    /**
     * Builds a [TFeature] by calling the [block] with a [TConfig] config instance as receiver.
     */
    fun prepare(block: TConfig.() -> Unit = {}): TFeature

    /**
     * Installs the [feature] class for a [HttpClient] defined at [scope].
     */
    fun install(feature: TFeature, scope: HttpClient)
}

/**
 * Try to get a [feature] installed in this client. Returns `null` if the feature was not previously installed.
 */
fun <B : Any, F : Any> HttpClient.feature(feature: HttpClientFeature<B, F>): F? =
    attributes.getOrNull(FEATURE_INSTALLED_LIST)?.getOrNull(feature.key)
