/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.utils.io.*
import kotlin.reflect.*

internal val DefaultCommonIgnoredTypes = setOf<KClass<*>>(
    ByteArray::class,
    String::class,
    HttpStatusCode::class,
    ByteReadChannel::class,
    OutgoingContent::class
)

internal expect val DefaultIgnoredTypes: Set<KClass<*>>

/**
 * A configuration for the [ContentNegotiation] plugin.
 */
@KtorDsl
public class ContentNegotiationConfig : Configuration {
    internal val registrations = mutableListOf<ConverterRegistration>()
    internal val acceptContributors = mutableListOf<AcceptHeaderContributor>()

    internal val ignoredTypes: MutableSet<KClass<*>> = (DefaultCommonIgnoredTypes + DefaultIgnoredTypes).toMutableSet()

    /**
     * Checks that the `ContentType` header value of a response suits the `Accept` header value of a request.
     */
    public var checkAcceptHeaderCompliance: Boolean = false

    /**
     * Registers a [contentType] to a specified [converter] with an optional converter [configuration].
     */
    public override fun <T : ContentConverter> register(
        contentType: ContentType,
        converter: T,
        configuration: T.() -> Unit
    ) {
        val registration = ConverterRegistration(contentType, converter.apply(configuration))
        registrations.add(registration)
    }

    /**
     * Registers a custom accepted content types [contributor].
     * A [contributor] function takes [io.ktor.server.application.ApplicationCall] and
     * a list of content types accepted according to the [HttpHeaders.Accept] header or provided by the previous
     * contributor if exists.
     * The result of this [contributor] should be a list of accepted content types with a quality.
     * A [contributor] could either keep or replace input list of accepted content types depending
     * on a use case. For example, a contributor taking the `format=json` request parameter could replace the original
     * content types list with the specified one from the uri argument.
     * Note that the returned list of accepted types will be sorted according to the quality using [sortedByQuality],
     * so a custom [contributor] may keep it unsorted and should not rely on input list order.
     */
    public fun accept(contributor: AcceptHeaderContributor) {
        acceptContributors.add(contributor)
    }

    /**
     * Adds a type to the list of types that should be ignored by [ContentNegotiation].
     *
     * The list contains the [HttpStatusCode] type by default.
     */
    public inline fun <reified T> ignoreType() {
        ignoreType(T::class)
    }

    /**
     * Remove [T] from the list of types that should be ignored by [ContentNegotiation].
     */
    public inline fun <reified T> removeIgnoredType() {
        removeIgnoredType(T::class)
    }

    /**
     * Remove [type] from the list of types that should be ignored by [ContentNegotiation].
     */
    public fun removeIgnoredType(type: KClass<*>) {
        ignoredTypes.remove(type)
    }

    /**
     * Adds a [type] to the list of types that should be ignored by [ContentNegotiation].
     *
     * The list contains the [HttpStatusCode], [ByteArray], [String] and streaming types by default.
     */
    public fun ignoreType(type: KClass<*>) {
        ignoredTypes.add(type)
    }

    /**
     * Clear all configured ignored types including defaults.
     */
    public fun clearIgnoredTypes() {
        ignoredTypes.clear()
    }
}
