/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.util.*
import kotlin.test.*

internal class URLBuilderTest {
    @Test
    fun testParseSchemeWithDigits() {
        testBuildString("a123://google.com")
    }

    @Test
    fun testParseSchemeWithDotsPlusAndMinusSigns() {
        testBuildString("a.+-://google.com")
    }

    @Test
    fun testParseSchemeWithCapitalCharacters() {
        testBuildString("HTTP://google.com")
    }

    @Test
    fun testParseSchemeNotStartedWithLetter() {
        for (index in 0..0x7F) {
            val char = index.toChar()

            if (char in 'a'..'z' || char in 'A'..'Z') {
                testBuildString("${char}http://google.com")
            } else {
                assertFails("Character $char is not allowed at the first position in the scheme.") {
                    testBuildString("${char}http://google.com")
                }
            }
        }
    }

    @Test
    fun portIsNotInStringIfItMatchesTheProtocolDefaultPort() {
        URLBuilder().apply {
            protocol = URLProtocol("custom", 12345)
            port = 12345
        }.buildString().let {
            assertEquals("custom://localhost/", it)
        }
    }

    @Test
    fun settingTheProtocolDoesNotOverwriteAnExplicitPort() {
        URLBuilder().apply {
            port = 8080
            protocol = URLProtocol.HTTPS
        }.buildString().let { url ->
            assertEquals("https://localhost:8080/", url)
        }
    }

    @Test
    fun protocolDefaultPortIsUsedIfAPortIsNotSpecified() {
        if (PlatformUtils.IS_BROWSER) return

        URLBuilder().apply {
            protocol = URLProtocol.HTTPS

            assertEquals(DEFAULT_PORT, port)
        }.build().also { url ->
            assertEquals(URLProtocol.HTTPS.defaultPort, url.port)
        }
    }

    @Test
    fun anExplicitPortIsUsedIfSpecified() {
        URLBuilder().apply {
            protocol = URLProtocol.HTTPS
            port = 2048

            assertEquals(2048, port)
        }.build().also { url ->
            assertEquals(2048, url.port)
        }
    }

    @Test
    fun takeFromACustomProtocolAndSettingTheDefaultPort() {
        URLBuilder().apply {
            takeFrom("custom://localhost/path")
            protocol = URLProtocol("custom", 8080)

            assertEquals(DEFAULT_PORT, port)
        }.buildString().also { url ->
            // ensure that the built url does not specify the port when configuring the default port
            assertEquals("custom://localhost/path", url)
        }
    }

    @Test
    fun rewritePathWhenRewriteUrl() {
        val url = URLBuilder("https://httpstat.us/301")
        url.takeFrom("https://httpstats.us")

        assertEquals("", url.encodedPath)
    }

    @Test
    fun rewritePathFromSlash() {
        val url = URLBuilder("https://httpstat.us/301")

        url.takeFrom("/")
        assertEquals("https://httpstat.us/", url.buildString())
    }

    @Test
    fun rewritePathFromSingle() {
        val url = URLBuilder("https://httpstat.us/301")

        url.takeFrom("/1")
        assertEquals("https://httpstat.us/1", url.buildString())
    }

    @Test
    fun rewritePathDirectoryWithRelative() {
        val url = URLBuilder("https://example.org/first/directory/")

        url.takeFrom("relative")
        assertEquals("https://example.org/first/directory/relative", url.buildString())
    }

    @Test
    fun rewritePathFileWithRelative() {
        val url = URLBuilder("https://example.org/first/file.html")

        url.takeFrom("relative")
        assertEquals("https://example.org/first/relative", url.buildString())
    }

    @Test
    fun rewritePathWithRoot() {
        val url = URLBuilder("https://example.com/api/v1/")
        url.takeFrom("/foo")
        assertEquals("https://example.com/foo", url.buildString())
    }

    @Test
    fun rewritePathFileWithDot() {
        val url = URLBuilder("https://example.org/first/file.html")

        url.takeFrom("./")
        assertEquals("https://example.org/first/./", url.buildString())
    }

    @Test
    fun rewriteHost() {
        val url = URLBuilder("https://example.com/api/v1")
        url.takeFrom("//other.com")
        assertEquals("https://other.com", url.buildString())
    }

    @Test
    fun queryParamsWithNoValue() {
        val url = URLBuilder("https://httpstat.us/?novalue")
        assertEquals("https://httpstat.us/?novalue", url.buildString())
    }

    @Test
    fun queryParamsWithEmptyValue() {
        val url = URLBuilder("https://httpstat.us/?empty=")
        assertEquals("https://httpstat.us/?empty=", url.buildString())
    }

    @Test
    fun emptyProtocolWithPort() {
        val url = URLBuilder("//whatever:8080/abc")

        assertEquals(URLProtocol.HTTP, url.protocol)
        assertEquals("whatever", url.host)
        assertEquals(8080, url.port)
        assertEquals("/abc", url.encodedPath)
    }

    @Test
    fun retainEmptyPath() {
        val url = URLBuilder("http://www.test.com")
        assertEquals("", url.encodedPath)
    }

    @Test
    fun testSurrogateInPath() {
        val url = URLBuilder("http://www.ktor.io/path/🐕")
        assertEquals("/path/🐕", url.encodedPath)
    }

    @Test
    fun testSurrogateInPathNotEncoded() {
        val url = URLBuilder().apply {
            appendPathSegments(listOf("path", "🐕"))
        }
        assertEquals("/path/%F0%9F%90%95", url.encodedPath)
    }

    @Test
    fun testPathEncoding() {
        val url = URLBuilder().apply {
            host = "ktor.io"
            port = 80
            pathSegments = listOf("id+test&test~test#test")
        }.buildString()

        assertEquals("http://ktor.io/id+test&test~test%23test", url)
    }

    @Test
    fun testFragmentSetters() {
        val urlBuilder = URLBuilder()

        urlBuilder.fragment = "as df"
        assertEquals(urlBuilder.encodedFragment, "as%20df")

        urlBuilder.encodedFragment = "as%25df"
        assertEquals("as%df", urlBuilder.fragment)
    }

    @Test
    fun testPathSetters() {
        val urlBuilder = URLBuilder()

        urlBuilder.pathSegments = listOf("as df")
        assertEquals(listOf("as%20df"), urlBuilder.encodedPathSegments)
        assertEquals("/as%20df", urlBuilder.encodedPath)

        urlBuilder.encodedPathSegments = listOf("as%25df")
        assertEquals(listOf("as%df"), urlBuilder.pathSegments)
        assertEquals("/as%25df", urlBuilder.encodedPath)

        urlBuilder.encodedPath = "as%3Ddf"
        assertEquals(listOf("as=df"), urlBuilder.pathSegments)
        assertEquals(listOf("as%3Ddf"), urlBuilder.encodedPathSegments)
    }

    @Test
    fun testUserSetters() {
        val urlBuilder = URLBuilder()

        urlBuilder.encodedUser = "as%20df"
        assertEquals("as df", urlBuilder.user)

        urlBuilder.user = "as%df"
        assertEquals("as%25df", urlBuilder.encodedUser)
    }

    @Test
    fun testPasswordSetters() {
        val urlBuilder = URLBuilder().apply { user = "asd" }

        urlBuilder.encodedPassword = "as%20df"
        assertEquals("as df", urlBuilder.password)

        urlBuilder.password = "as%df"
        assertEquals("as%25df", urlBuilder.encodedPassword)
    }

    @Test
    fun testQuerySetters() {
        val urlBuilder = URLBuilder()

        urlBuilder.encodedParameters.append("as%20df", "as%25df")
        assertEquals("as%df", urlBuilder.parameters["as df"])

        urlBuilder.parameters.remove("as df")

        urlBuilder.parameters.append("as%df", "as df")
        assertEquals("as+df", urlBuilder.encodedParameters["as%25df"])
    }

    /**
     * Checks that the given [url] and the result of [URLBuilder.buildString] is equal (case insensitive).
     */
    private fun testBuildString(url: String) {
        assertEquals(url.lowercase(), URLBuilder(url).buildString().lowercase())
    }
}
