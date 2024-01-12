/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.resources

import io.ktor.resources.*
import io.ktor.resources.serialization.*
import kotlin.test.*

class ResourceUrlBuilderTest {

    private val resourcesFormat = ResourcesFormat()

    @Resource("resource/{id}/")
    data class SimpleResource(
        val id: Int
    )

    @Test
    fun testSimpleResource() {
        val resource = SimpleResource(123)
        val url = href(resourcesFormat, resource)
        assertEquals("/resource/123/", url)
    }

    @Resource("resource/{id}")
    data class SimpleResourceWithQuery(
        val id: Int,
        val key: String
    )

    @Test
    fun testSimpleResourceWithQuery() {
        val resource = SimpleResourceWithQuery(123, "456")
        val url = href(resourcesFormat, resource)
        assertEquals("/resource/123?key=456", url)
    }

    @Resource("resource/{id}")
    data class SimpleResourceWithQueryList(
        val id: Int,
        val key: List<String>
    )

    @Test
    fun testSimpleResourceWithQueryList() {
        val resource = SimpleResourceWithQueryList(123, listOf("456", "789"))
        val url = href(resourcesFormat, resource)
        assertEquals("/resource/123?key=456&key=789", url)
    }

    @Resource("resource/{ids...}")
    data class SimpleResourceWithWildcard(
        val ids: List<String>
    )

    @Test
    fun testSimpleResourceWithWildcard() {
        val resource1 = SimpleResourceWithWildcard(listOf("456", "789"))
        val url1 = href(resourcesFormat, resource1)
        assertEquals("/resource/456/789", url1)

        val resource2 = SimpleResourceWithWildcard(emptyList())
        val url2 = href(resourcesFormat, resource2)
        assertEquals("/resource", url2)
    }

    @Resource("resource/{id?}")
    data class SimpleResourceWithNullable(
        val id: Boolean?
    )

    @Test
    fun testSimpleResourceWithNullable() {
        val resource1 = SimpleResourceWithNullable(true)
        val url1 = href(resourcesFormat, resource1)
        assertEquals("/resource/true", url1)

        val resource2 = SimpleResourceWithNullable(null)
        val url2 = href(resourcesFormat, resource2)
        assertEquals("/resource", url2)
    }

    @Resource("user/{user}")
    data class NestedResource(
        val user: String,
        val parent: SimpleResource
    )

    @Test
    fun testNestedResource() {
        val resource = NestedResource("me", SimpleResource(123))
        val url = href(resourcesFormat, resource)
        assertEquals("/resource/123/user/me", url)
    }

    @Resource("user/{id}")
    class ResourceWithoutParameter

    @Test
    fun testResourceWithoutParameter() {
        val resource = ResourceWithoutParameter()
        assertFailsWith<ResourceSerializationException> {
            href(resourcesFormat, resource)
        }.let {
            assertEquals("Expect exactly one parameter with name: id, but found 0", it.message)
        }
    }

    @Resource("user/{id}")
    class ResourceWithExtraParameter(
        val id: List<String>
    )

    @Test
    fun testResourceWithExtraParameter() {
        val resource = ResourceWithExtraParameter(listOf("1", "2"))
        assertFailsWith<ResourceSerializationException> {
            href(resourcesFormat, resource)
        }.let {
            assertEquals("Expect exactly one parameter with name: id, but found 2", it.message)
        }
    }

    @Resource("user/{id?}")
    class ResourceWithExtraNullableParameter(
        val id: List<String>
    )

    @Test
    fun testResourceWithExtraNullableParameter() {
        val resource = ResourceWithExtraNullableParameter(listOf("1", "2"))
        assertFailsWith<ResourceSerializationException> {
            href(resourcesFormat, resource)
        }.let {
            assertEquals("Expect zero or one parameter with name: id, but found 2", it.message)
        }
    }
}
