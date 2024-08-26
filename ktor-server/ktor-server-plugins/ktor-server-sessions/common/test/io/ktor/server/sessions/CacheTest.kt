/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class CacheTest {
    @Test
    fun testBaseSimpleCase() = runTest {
        var counter = 0

        val cache = BaseCache<Int, String> { counter++; it.toString() }

        assertEquals("1", cache.getOrCompute(1))
        assertEquals(1, counter)

        assertEquals("1", cache.getOrCompute(1))
        assertEquals(1, counter)

        assertEquals("2", cache.getOrCompute(2))
        assertEquals(2, counter)
    }

    @Test
    fun testBlocking() = runTest {
        val latch = Job()
        var ref = ""

        val cache = BaseCache<Int, String> { if (it == 0) latch.join(); it.toString() }

        assertEquals("1", cache.getOrCompute(1))
        val th = launch(Dispatchers.Default) {
            ref = cache.getOrCompute(0)
        }

        assertEquals("2", cache.getOrCompute(2))

        latch.complete()
        th.join()

        assertEquals("0", ref)
    }

    @Test
    fun testPeek() = runTest {
        val cache = BaseCache<Int, String> { fail(""); }
        assertNull(cache.peek(1))
    }

    @Test
    fun testInvalidate() = runTest {
        var counter = 0
        val cache = BaseCache<Int, String> { counter++; it.toString() }

        assertEquals("1", cache.getOrCompute(1))
        assertEquals(1, counter)

        cache.invalidate(1)

        assertEquals("1", cache.getOrCompute(1))
        assertEquals(2, counter)

        cache.invalidate(1, "shouldn't invalidate here")
        assertEquals("1", cache.getOrCompute(1))
        assertEquals(2, counter)

        cache.invalidate(1, "1")
        assertEquals("1", cache.getOrCompute(1))
        assertEquals(3, counter)
    }

    @Test
    fun testInvalidateWithError() = runTest {
        class ExpectedException : Exception()

        var counter = 0
        val cache = BaseCache<Int, String> { counter++; throw ExpectedException() }

        assertFailsWith<ExpectedException> {
            cache.getOrCompute(1)
        }

        assertEquals(1, counter)

        assertFailsWith<ExpectedException> {
            cache.getOrCompute(1)
        }
        assertEquals(1, counter)

        cache.invalidate(1)
        assertEquals(1, counter)

        assertFailsWith<ExpectedException> {
            cache.getOrCompute(1)
        }
        assertEquals(2, counter)
    }

    @Test
    fun testStoreSameValueDoesntTriggerDelegate() = runTest {
        var readCount = 0
        var writeCount = 0
        var invalidateCount = 0
        val memoryStorage = object : SessionStorage {
            val storage = mutableMapOf<String, String>()
            override suspend fun write(id: String, value: String) {
                writeCount++
                storage[id] = value
            }

            override suspend fun invalidate(id: String) {
                invalidateCount++
                storage.remove(id)
            }

            override suspend fun read(id: String): String {
                readCount++
                return storage[id]!!
            }
        }
        val storage = CacheStorage(memoryStorage, 100)

        storage.write("id", "123")
        assertEquals("123", storage.read("id"))

        assertEquals(2, readCount) // compute + read
        assertEquals(1, writeCount)

        storage.write("id", "123")
        assertEquals("123", storage.read("id"))

        assertEquals(2, readCount) // no additional read
        assertEquals(1, writeCount)

        storage.write("id", "234")
        assertEquals("234", storage.read("id"))

        assertEquals(3, readCount) // additional read after invalidation
        assertEquals(2, writeCount)

        assertEquals(0, invalidateCount)
    }
}
