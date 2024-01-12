/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.server.util.*
import io.ktor.utils.io.*
import kotlin.test.*

@OptIn(InternalAPI::class)
class CopyOnWriteHashMapTest {
    private val map = CopyOnWriteHashMap<String, String>()

    @Test
    fun smoke() {
        assertEquals(null, map["k1"])
        map["k1"] = "v1"
        assertEquals("v1", map["k1"])

        assertEquals(null, map["k2"])
        assertEquals(null, map.put("k2", "v2"))
        assertEquals("v2", map.put("k2", "v3"))
        assertEquals("v3", map.remove("k2"))

        assertEquals("v31", map.computeIfAbsent("k3") { "v31" })
        assertEquals("v31", map.computeIfAbsent("k3") { "v32" })
    }

    @Test
    fun getEmpty() {
        assertNull(map["k1"])
    }

    @Test
    fun getExisting() {
        map.put("k1", "v1")
        assertEquals("v1", map["k1"])
    }

    @Test
    fun putNewAndReplace() {
        assertEquals(null, map.put("k1", "v1"))
        assertEquals("v1", map.put("k1", "v2"))
        assertEquals("v2", map.put("k1", "v3"))
        assertEquals("v3", map["k1"])
    }

    @Test
    fun putMany() {
        repeat(100) {
            assertNull(map.put("k-$it", "v-$it"))
        }

        repeat(100) {
            assertEquals("v-$it", map["k-$it"])
        }
    }

    @Test
    fun testRemoveMissing() {
        assertNull(map["k"])
        assertNull(map.remove("k"))
        assertNull(map["k"])
    }

    @Test
    fun testRemoveExisting() {
        map.put("k1", "v1")
        assertEquals("v1", map.remove("k1"))
        assertNull(map.remove("k1"))
        assertNull(map["k1"])
    }

    @Test
    fun testRemoveOnlySpecified() {
        map.put("k1", "v1")
        map.put("k2", "v2")

        assertEquals("v1", map["k1"])
        assertEquals("v2", map["k2"])
        assertEquals("v1", map.remove("k1"))
        assertEquals(null, map["k1"])
        assertEquals("v2", map["k2"])
    }

    @Test
    fun testComputeIfAbsentMissing() {
        assertNull(map["k1"])
        assertEquals("v1", map.computeIfAbsent("k1") { "v1" })
        assertEquals("v1", map["k1"])
    }

    @Test
    fun testComputeIfAbsentExisting() {
        map.put("k1", "v0")
        assertEquals("v0", map.computeIfAbsent("k1") { fail("shouldn't be invoked") })
        assertEquals("v0", map["k1"])
    }
}
