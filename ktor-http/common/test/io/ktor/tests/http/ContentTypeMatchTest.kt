/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class ContentTypeMatchTest {
    @Test
    fun testTypeAndSubtype() {
        assertTrue { ContentType.parse("text/plain").match("*") }
        assertTrue { ContentType.parse("text/plain").match("* ") }
        assertTrue { ContentType.parse("text/plain").match("*/*") }
        assertTrue { ContentType.parse("text/plain").match("*/ *") }
        assertTrue { ContentType.parse("text/plain").match("*/plain") }
        assertTrue { ContentType.parse("text/plain").match("* /plain") }
        assertTrue { ContentType.parse("text/PLAIN").match("*/plain") }
        assertTrue { ContentType.parse("text/plain").match("text/*") }
        assertTrue { ContentType.parse("text/plain").match("text/plain") }
        assertTrue { ContentType.parse("text/plain").match("TEXT/plain") }

        assertFailsWith<BadContentTypeFormatException> { ContentType.parse("text/") }
        assertFailsWith<BadContentTypeFormatException> { ContentType.parse("/plain") }
        assertFailsWith<BadContentTypeFormatException> { ContentType.parse("foobar") }
        assertFailsWith<BadContentTypeFormatException> { ContentType.parse("foo/bar/baz") }

        assertFalse(ContentType.parse("text/plain").match("image/plain"))
        assertFalse(ContentType.parse("text/plain").match("text/xml"))
    }

    @Test
    fun testParametersConstants() {
        assertTrue { ContentType.parse("a/b; a=1").match("*/*; a=1") }
        assertTrue { ContentType.parse("a/b; A=1").match("*/*; a=1") }
        assertFalse(ContentType.parse("a/b").match("*/*; a=2"))
        assertFalse(ContentType.parse("a/b; a=1").match("*/*; a=2"))
        assertFalse(ContentType.parse("a/b; A=1").match("*/*; a=2"))
    }

    @Test
    fun testParametersWithSubtype() {
        assertTrue { ContentType.parse("a/b; a=1").match("a/b") }
        assertTrue { ContentType.parse("a/b; a=xyz").match("a/b; a=XYZ") }
    }

    @Test
    fun testParametersValueWildcard() {
        assertTrue(ContentType.parse("a/b; a=1").match("*/*; a=*"))
        assertFalse(ContentType.parse("a/b; b=1").match("*/*; a=*"))
    }

    @Test
    fun testParametersNameWildcard() {
        assertTrue(ContentType.parse("a/b; a=1").match("*/*; *=1"))
        assertTrue(ContentType.parse("a/b; a=X").match("*/*; *=x"))
        assertFalse(ContentType.parse("a/b; a=2").match("*/*; *=1"))
        assertFalse(ContentType.parse("a/b; a=y").match("*/*; *=x"))
    }

    @Test
    fun testParametersAllWildcard() {
        assertTrue(ContentType.parse("a/b; a=2").match("*/*; *=*"))
        assertTrue(ContentType.parse("a/b").match("*/*; *=*"))
    }

    @Test
    fun testContentTypeConst() {
        assertTrue { ContentType.Application.FormUrlEncoded.match(ContentType.Application.FormUrlEncoded) }
        assertFalse(ContentType.Application.Json.match(ContentType.Application.FormUrlEncoded))
    }
}
