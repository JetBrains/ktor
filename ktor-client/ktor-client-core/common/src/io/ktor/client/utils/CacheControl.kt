/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

/**
 * List of [CacheControl] known values.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.utils.CacheControl)
 */
@Suppress("KDocMissingDocumentation", "MemberVisibilityCanBePrivate")
public object CacheControl {
    public const val MAX_AGE: String = "max-age"
    public const val MIN_FRESH: String = "min-fresh"
    public const val ONLY_IF_CACHED: String = "only-if-cached"

    public const val MAX_STALE: String = "max-stale"
    public const val NO_CACHE: String = "no-cache"
    public const val NO_STORE: String = "no-store"
    public const val NO_TRANSFORM: String = "no-transform"

    public const val MUST_REVALIDATE: String = "must-revalidate"
    public const val PUBLIC: String = "public"
    public const val PRIVATE: String = "private"
    public const val PROXY_REVALIDATE: String = "proxy-revalidate"
    public const val S_MAX_AGE: String = "s-maxage"
}
