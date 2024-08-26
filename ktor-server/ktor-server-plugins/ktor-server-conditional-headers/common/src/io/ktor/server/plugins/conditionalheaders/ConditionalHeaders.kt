/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.conditionalheaders

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.reflect.*

/**
 * A configuration for the [ConditionalHeaders] plugin.
 */
@KtorDsl
public class ConditionalHeadersConfig {
    internal val versionProviders = mutableListOf<suspend (ApplicationCall, OutgoingContent) -> List<Version>>()

    init {
        versionProviders.add { _, content -> content.versions }
        versionProviders.add { call, content ->
            content.headers.parseVersions().takeIf { it.isNotEmpty() }
                ?: call.response.headers.allValues().parseVersions()
        }
    }

    /**
     * Registers a function that can fetch a version list for a given [ApplicationCall] and [OutgoingContent].
     *
     * @see [ConditionalHeaders]
     */
    public fun version(provider: suspend (ApplicationCall, OutgoingContent) -> List<Version>) {
        versionProviders.add(provider)
    }
}

internal val VersionProvidersKey: AttributeKey<List<suspend (ApplicationCall, OutgoingContent) -> List<Version>>> =
    AttributeKey("ConditionalHeadersKey", typeOf<List<*>>())

/**
 * Retrieves versions such as [LastModifiedVersion] or [EntityTagVersion] for a given content.
 */
public suspend fun ApplicationCall.versionsFor(content: OutgoingContent): List<Version> {
    val versionProviders = application.attributes.getOrNull(VersionProvidersKey)
    return versionProviders?.flatMap { it(this, content) } ?: emptyList()
}

/**
 * A plugin that avoids sending the body of content if it has not changed since the last request.
 * This is achieved by using the following headers:
 * - The `Last-Modified` response header contains a resource modification time.
 *    For example, if the client request contains the `If-Modified-Since` value,
 *    Ktor will send a full response only if a resource has been modified after the given date.
 * - The `Etag` response header is an identifier for a specific resource version.
 *    For instance, if the client request contains the `If-None-Match` value,
 *    Ktor won't send a full response in case this value matches the `Etag`.
 *
 * The code snippet below shows how to add a `Etag` and `Last-Modified` headers for `CSS`:
 * ```kotlin
 * install(ConditionalHeaders) {
 *     version { call, outgoingContent ->
 *         when (outgoingContent.contentType?.withoutParameters()) {
 *             ContentType.Text.CSS -> listOf(EntityTagVersion("abc123"), LastModifiedVersion(Date(1646387527500)))
 *             else -> emptyList()
 *         }
 *     }
 * }
 * ```
 *
 * You can learn more from [Conditional headers](https://ktor.io/docs/conditional-headers.html).
 */
public val ConditionalHeaders: RouteScopedPlugin<ConditionalHeadersConfig> = createRouteScopedPlugin(
    "ConditionalHeaders",
    ::ConditionalHeadersConfig
) {
    val versionProviders = pluginConfig.versionProviders

    application.attributes.put(VersionProvidersKey, versionProviders)

    fun checkVersions(call: ApplicationCall, versions: List<Version>): VersionCheckResult {
        for (version in versions) {
            val result = version.check(call.request.headers)
            if (result != VersionCheckResult.OK) {
                return result
            }
        }
        return VersionCheckResult.OK
    }

    on(ResponseBodyReadyForSend) { call, content ->
        val versions = call.versionsFor(content)

        if (versions.isNotEmpty()) {
            val headers = Headers.build {
                versions.forEach { it.appendHeadersTo(this) }
            }

            val responseHeaders = call.response.headers
            headers.forEach { name, values ->
                if (!responseHeaders.contains(name)) {
                    values.forEach { responseHeaders.append(name, it) }
                }
            }
        }

        val checkResult = checkVersions(call, versions)
        if (checkResult != VersionCheckResult.OK) {
            transformBodyTo(HttpStatusCodeContent(checkResult.statusCode))
        }
    }
}

/**
 * Retrieves the `LastModified` and `ETag` versions from headers.
 */
public fun Headers.parseVersions(): List<Version> {
    val lastModifiedHeaders = getAll(HttpHeaders.LastModified) ?: emptyList()
    val etagHeaders = getAll(HttpHeaders.ETag) ?: emptyList()
    val versions = ArrayList<Version>(lastModifiedHeaders.size + etagHeaders.size)

    lastModifiedHeaders.mapTo(versions) {
        LastModifiedVersion(it.fromHttpToGmtDate())
    }
    etagHeaders.mapTo(versions) { EntityTagVersion(it) }

    return versions
}
