/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cache.storage.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.test.*

class CachingCacheStorageTest {

    @Test
    fun testFindAllLoadsDataFromDelegate(): Unit = runBlocking {
        val delegate = InMemoryCacheStorage()
        delegate.store(Url("http://example.com"), data())
        delegate.store(Url("http://example.com"), data(mapOf("key" to "value")))

        val storage = CachingCacheStorage(delegate)
        assertEquals(2, storage.findAll(Url("http://example.com")).size)
    }

    @Test
    fun testFindAllUsesCaching(): Unit = runBlocking {
        val delegate = InMemoryCacheStorage()
        delegate.store(Url("http://example.com"), data())
        delegate.store(Url("http://example.com"), data(mapOf("key" to "value")))

        val storage = CachingCacheStorage(delegate)
        val result1 = storage.findAll(Url("http://example.com")).size
        val result2 = storage.findAll(Url("http://example.com")).size
        assertEquals(2, result1)
        assertEquals(result1, result2)
        assertEquals(1, delegate.findAllCalledCount)
    }

    @Test
    fun testFindLoadsDataFromDelegate(): Unit = runBlocking {
        val delegate = InMemoryCacheStorage()
        delegate.store(Url("http://example.com"), data())
        delegate.store(Url("http://example.com"), data(mapOf("key" to "value")))

        val storage = CachingCacheStorage(delegate)
        assertNotNull(storage.find(Url("http://example.com"), mapOf("key" to "value")))
    }

    @Test
    fun testFindUsesCaching(): Unit = runBlocking {
        val delegate = InMemoryCacheStorage()
        delegate.store(Url("http://example.com"), data())
        delegate.store(Url("http://example.com"), data(mapOf("key" to "value")))

        val storage = CachingCacheStorage(delegate)
        val result1 = storage.find(Url("http://example.com"), mapOf("key" to "value"))
        val result2 = storage.find(Url("http://example.com"), mapOf("key" to "value"))
        assertNotNull(result1)
        assertEquals(result1, result2)
        assertEquals(1, delegate.findAllCalledCount)
    }

    @Test
    fun testStoreUpdatesCaching(): Unit = runBlocking {
        val delegate = InMemoryCacheStorage()
        delegate.store(Url("http://example.com"), data())

        val storage = CachingCacheStorage(delegate)
        assertEquals(1, storage.findAll(Url("http://example.com")).size)

        storage.store(Url("http://example.com"), data(mapOf("key" to "value")))
        assertEquals(2, storage.findAll(Url("http://example.com")).size)
        assertNotNull(storage.find(Url("http://example.com"), mapOf("key" to "value")))
    }

    private fun data(varyKeys: Map<String, String> = emptyMap()) = CachedResponseData(
        Url("http://example.com"),
        HttpStatusCode.OK,
        GMTDate(),
        GMTDate(),
        HttpProtocolVersion.HTTP_1_1,
        GMTDate(),
        headersOf(),
        varyKeys,
        ByteArray(0)
    )
}

private class InMemoryCacheStorage : CacheStorage {

    private val store = mutableMapOf<Url, MutableSet<CachedResponseData>>()
    var findCalledCount = 0
    var findAllCalledCount = 0

    override suspend fun store(url: Url, data: CachedResponseData) {
        val cache = store.computeIfAbsent(url) { mutableSetOf() }
        cache.add(data)
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        findCalledCount++
        val cache = store.computeIfAbsent(url) { mutableSetOf() }
        return cache.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        findAllCalledCount++
        val cache = store.computeIfAbsent(url) { mutableSetOf() }
        return cache.toSet()
    }
}
