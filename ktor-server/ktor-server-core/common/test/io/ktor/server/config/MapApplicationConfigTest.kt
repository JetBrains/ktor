/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import kotlin.test.*

class MapApplicationConfigTest {

    @Test
    fun testMapApplicationConfig() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")

        mapConfig.put("auth.values.size", "2")
        mapConfig.put("auth.values.0", "a")
        mapConfig.put("auth.values.1", "b")

        mapConfig.put("auth.listValues", listOf("a", "b", "c"))

        val auth = mapConfig.config("auth")
        assertEquals("ktor", auth.property("salt").getString())
        val users = auth.configList("users")
        assertEquals(1, users.size)
        assertEquals("test", users[0].property("name").getString())

        assertEquals(listOf("a", "b", "c"), auth.property("listValues").getList())

        val values = auth.property("values").getList()
        assertEquals("[a, b]", values.toString())

        assertEquals(null, auth.propertyOrNull("missingProperty"))
        assertEquals("SHA-256", auth.propertyOrNull("hashAlgorithm")?.getString())
        assertEquals(listOf("a", "b", "c"), auth.propertyOrNull("listValues")?.getList())

        assertEquals(null, mapConfig.propertyOrNull("missingProperty"))
        assertEquals(null, mapConfig.propertyOrNull("auth.missingProperty"))
        assertEquals("SHA-256", mapConfig.propertyOrNull("auth.hashAlgorithm")?.getString())
        assertEquals(listOf("a", "b", "c"), mapConfig.propertyOrNull("auth.listValues")?.getList())
    }

    @Test
    fun testMapApplicationConfigNestedPut() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.salt", "ktor")

        val nested = mapConfig.config("auth") as MapApplicationConfig
        nested.put("value1", "data1")

        assertEquals("ktor", mapConfig.property("auth.salt").getString())
        assertEquals("data1", mapConfig.property("auth.value1").getString())
        assertEquals("data1", nested.property("value1").getString())
    }

    @Test
    fun testKeysTopLevelMapConfig() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")

        mapConfig.put("auth.values.size", "2")
        mapConfig.put("auth.values.0", "a")
        mapConfig.put("auth.values.1", "b")

        mapConfig.put("auth.listValues", listOf("a", "b", "c"))

        mapConfig.put("auth.data.value1", "1")
        mapConfig.put("auth.data.value2", "2")

        val keys = mapConfig.keys()
        assertEquals(
            keys,
            setOf(
                "auth.hashAlgorithm",
                "auth.salt",
                "auth.users",
                "auth.values",
                "auth.listValues",
                "auth.data.value1",
                "auth.data.value2"
            )
        )
    }

    @Test
    fun testKeysNestedMapConfig() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.nested.data.value1", "1")
        mapConfig.put("auth.nested.data.value2", "2")
        mapConfig.put("auth.nested.list.size", "2")
        mapConfig.put("auth.nested.list.0", "a")
        mapConfig.put("auth.nested.list.1", "b")
        mapConfig.put("auth.data1.value1", "other value")

        val nestedConfig = mapConfig.config("auth.nested")
        val keys = nestedConfig.keys()
        assertEquals(keys, setOf("data.value1", "data.value2", "list"))
        assertEquals("1", nestedConfig.property("data.value1").getString())
        assertEquals("2", nestedConfig.property("data.value2").getString())
        assertEquals(listOf("a", "b"), nestedConfig.property("list").getList())
    }

    @Test
    fun testToMap() {
        val config = MapApplicationConfig()
        config.put("hashAlgorithm", "SHA-256")
        config.put("salt", "ktor")
        config.put("values", listOf("a", "b"))
        config.put("listValues", listOf("a", "b", "c"))
        config.put("data.value1", "1")
        config.put("data.value2", "2")
        config.put("users.size", "2")
        config.put("users.0.name", "test")
        config.put("users.0.password", "asd")
        config.put("users.1.name", "other")
        config.put("users.1.password", "qwe")
        config.put("nested.config.value", "a1")

        val map = config.toMap()

        assertEquals(7, map.size)
        assertEquals("SHA-256", map["hashAlgorithm"])
        assertEquals("ktor", map["salt"])
        assertEquals(
            listOf(mapOf("name" to "test", "password" to "asd"), mapOf("name" to "other", "password" to "qwe")),
            map["users"]
        )
        assertEquals(listOf("a", "b"), map["values"])
        assertEquals(listOf("a", "b", "c"), map["listValues"])
        assertEquals(mapOf("value1" to "1", "value2" to "2"), map["data"])

        @Suppress("UNCHECKED_CAST")
        assertEquals("a1", (map["nested"] as Map<String, Map<String, String>>)["config"]!!["value"])
    }
}
