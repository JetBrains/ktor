/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cachingheaders

import io.ktor.http.*

/**
 * Merge a list of cache control directives.
 *
 * Currently, visibility is pinned to the expiration directive,
 * then to the first no-cache,
 * then to the no-store directive.
 * The RFC doesn't state where a visibility modifier should be, so we can place it at any place
 * so there is nothing behind the rule beyond, therefore, could be changed.
 *
 * Only one visibility specifier is kept.
 *
 * If there are different visibility modifiers specified, then the private wins.
 * All max ages directives are reduced to a single with all minimal max age values.
 *
 * A no-cache directive is always placed first.
 * A no-store directive is always placed after no-cache, otherwise it's placed first.
 * A max-age directive is always the last.
 *
 * Revalidation directives are collected as well.
 * Currently, revalidation directives are tied to max age by-design.
 * This is not fair, according to RFC, so it will be changed in the future.
 */
internal fun List<CacheControl>.mergeCacheControlDirectives(): List<CacheControl> {
    if (size < 2) return this

    val visibility = when {
        any { it.visibility == CacheControl.Visibility.Private } -> CacheControl.Visibility.Private
        any { it.visibility == CacheControl.Visibility.Public } -> CacheControl.Visibility.Public
        else -> null
    }

    val noCacheDirective = firstOrNull { it is CacheControl.NoCache } as CacheControl.NoCache?
    val noStoreDirective = firstOrNull { it is CacheControl.NoStore } as CacheControl.NoStore?

    val maxAgeDirectives = filterIsInstance<CacheControl.MaxAge>()
    val minMaxAge = maxAgeDirectives.minByOrNull { it.maxAgeSeconds }?.maxAgeSeconds
    val minProxyMaxAge = maxAgeDirectives.minByOrNull { it.proxyMaxAgeSeconds ?: Int.MAX_VALUE }?.proxyMaxAgeSeconds
    val mustRevalidate = maxAgeDirectives.any { it.mustRevalidate }
    val proxyRevalidate = maxAgeDirectives.any { it.proxyRevalidate }

    return mutableListOf<CacheControl>().apply {
        noCacheDirective?.let { add(CacheControl.NoCache(null)) }
        noStoreDirective?.let { add(CacheControl.NoStore(null)) }

        this@mergeCacheControlDirectives
            .filter {
                it !is CacheControl.NoCache && it !is CacheControl.NoStore && it !is CacheControl.MaxAge
            }
            .let { addAll(it) }

        if (maxAgeDirectives.isNotEmpty()) {
            add(
                CacheControl.MaxAge(
                    minMaxAge!!,
                    minProxyMaxAge,
                    mustRevalidate,
                    proxyRevalidate,
                    visibility
                )
            )
        } else {
            when {
                noCacheDirective != null -> set(0, CacheControl.NoCache(visibility))
                noStoreDirective != null -> set(0, CacheControl.NoStore(visibility))
            }
        }
    }
}
