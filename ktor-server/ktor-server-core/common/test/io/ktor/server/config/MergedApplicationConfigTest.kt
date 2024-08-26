/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import kotlin.test.*

class MergedApplicationConfigTest {

    @Test
    fun testProperty() {
        val first = MapApplicationConfig("first" to "value1")
        val second = MapApplicationConfig("first" to "value2", "second" to "value3")
        val config = MergedApplicationConfig(first, second)
        assertEquals("value1", config.property("first").getString())
        assertEquals("value3", config.property("second").getString())
    }

    @Test
    fun testConfig() {
        val first = MapApplicationConfig("first.second.third1" to "value1", "first.second.third2" to "value2")
        val second = MapApplicationConfig("first.second.third1" to "ignored", "first.second.third3" to "value3")
        val config = MergedApplicationConfig(first, second)
        val mergedNestedConfig = config.config("first.second")
        assertEquals("value1", mergedNestedConfig.property("third1").getString())
        assertEquals("value2", mergedNestedConfig.property("third2").getString())
        assertEquals("value3", mergedNestedConfig.property("third3").getString())
        assertEquals(setOf("third1", "third2", "third3"), mergedNestedConfig.keys())
    }

    @Test
    fun testConfigList() {
        val first = MapApplicationConfig(
            "first.second.size" to "2",
            "first.second.0.third" to "value1",
            "first.second.1.third" to "value2"
        )
        val second = MapApplicationConfig(
            "first.second.size" to "2",
            "first.second.0.third" to "value3",
            "first.second.1.third" to "value4"
        )
        val config = MergedApplicationConfig(first, second)
        val configs = config.configList("first.second")
        assertEquals(4, configs.size)
        assertEquals("value1", configs[0].property("third").getString())
        assertEquals("value2", configs[1].property("third").getString())
        assertEquals("value3", configs[2].property("third").getString())
        assertEquals("value4", configs[3].property("third").getString())
    }

    @Test
    fun testConfigListWhenOneHasNoKey() {
        val first = MapApplicationConfig(
            "first1.second.size" to "2",
            "first1.second.0.third" to "value1",
            "first1.second.1.third" to "value2"
        )
        val second = MapApplicationConfig(
            "first2.second.size" to "2",
            "first2.second.0.third" to "value1",
            "first2.second.1.third" to "value2"
        )
        val config = MergedApplicationConfig(first, second)
        val configs1 = config.configList("first1.second")
        assertEquals(2, configs1.size)
        assertEquals("value1", configs1[0].property("third").getString())
        assertEquals("value2", configs1[1].property("third").getString())
        val configs2 = config.configList("first2.second")
        assertEquals(2, configs2.size)
        assertEquals("value1", configs2[0].property("third").getString())
        assertEquals("value2", configs2[1].property("third").getString())
    }

    @Test
    fun testPropertyOrNull() {
        val first = MapApplicationConfig("first" to "value1")
        val second = MapApplicationConfig("first" to "value2", "second" to "value3")
        val config = MergedApplicationConfig(first, second)
        assertEquals("value1", config.property("first").getString())
        assertEquals("value3", config.property("second").getString())
        assertNull(config.propertyOrNull("third"))
    }

    @Test
    fun testKeys() {
        val first = MapApplicationConfig("first" to "value1")
        val second = MapApplicationConfig("first" to "value2", "second" to "value3")
        val config = MergedApplicationConfig(first, second)
        assertEquals(setOf("first", "second"), config.keys())
    }

    @Test
    fun testToMap() {
        val first = MapApplicationConfig("first" to "value1")
        val second = MapApplicationConfig("first" to "value2", "second" to "value3")
        val config = MergedApplicationConfig(first, second)
        assertEquals(mapOf("first" to "value1", "second" to "value3"), config.toMap())
    }

    @Test
    fun testMergeWith() {
        val first = MapApplicationConfig(
            "value1" to "1",
            "value2" to "2",
            "value3" to "3",
            "value4" to "4"
        )
        val second = MapApplicationConfig(
            "value1" to "2",
            "value2" to "2",
            "value3" to "1",
        )
        val mergedConfig = first.mergeWith(second)
        assertEquals("2", mergedConfig.property("value1").getString())
        assertEquals("2", mergedConfig.property("value2").getString())
        assertEquals("1", mergedConfig.property("value3").getString())
        assertEquals("4", mergedConfig.property("value4").getString())
    }

    @Test
    fun testMergeWithFallback() {
        val first = MapApplicationConfig(
            "value1" to "1",
            "value2" to "2",
            "value3" to "3"
        )
        val second = MapApplicationConfig(
            "value1" to "2",
            "value2" to "2",
            "value3" to "1",
            "value4" to "4"
        )
        val mergedConfig = first.withFallback(second)
        assertEquals("1", mergedConfig.property("value1").getString())
        assertEquals("2", mergedConfig.property("value2").getString())
        assertEquals("3", mergedConfig.property("value3").getString())
        assertEquals("4", mergedConfig.property("value4").getString())
    }
}
