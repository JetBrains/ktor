/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cache.storage

import io.ktor.client.plugins.cache.*
import io.ktor.http.*
import io.ktor.util.collections.*

@Suppress("DEPRECATION_ERROR")
internal class UnlimitedCacheStorage : HttpCacheStorage() {
    private val store = ConcurrentMap<Url, MutableSet<HttpCacheEntry>>()

    override fun store(url: Url, value: HttpCacheEntry) {
        val data = store.computeIfAbsent(url) { ConcurrentSet() }
        if (!data.add(value)) {
            data.remove(value)
            data.add(value)
        }
    }

    override fun find(url: Url, varyKeys: Map<String, String>): HttpCacheEntry? {
        val data = store.computeIfAbsent(url) { ConcurrentSet() }
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override fun findByUrl(url: Url): Set<HttpCacheEntry> = store[url] ?: emptySet()
}

internal class UnlimitedStorage : CacheStorage {

    private val store = ConcurrentMap<Url, MutableSet<CachedResponseData>>()

    override suspend fun store(url: Url, data: CachedResponseData) {
        val cache = store.computeIfAbsent(url) { ConcurrentSet() }
        if (!cache.add(data)) {
            cache.remove(data)
            cache.add(data)
        }
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        val data = store.computeIfAbsent(url) { ConcurrentSet() }
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> = store[url] ?: emptySet()
}
