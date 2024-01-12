/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cache.storage

import io.ktor.client.plugins.cache.*
import io.ktor.http.*

@Suppress("DEPRECATION_ERROR")
internal object DisabledCacheStorage : HttpCacheStorage() {
    override fun store(url: Url, value: HttpCacheEntry) {}

    override fun find(url: Url, varyKeys: Map<String, String>): HttpCacheEntry? = null

    override fun findByUrl(url: Url): Set<HttpCacheEntry> = emptySet()
}

internal object DisabledStorage : CacheStorage {
    override suspend fun store(url: Url, data: CachedResponseData) {}
    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? = null
    override suspend fun findAll(url: Url): Set<CachedResponseData> = emptySet()
}
