import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.date.*
import kotlin.test.*
import kotlin.time.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@ExperimentalTime
class CookiesTest {

    @Test
    fun testCookiesEscape() = testSuspend {
        val testClock = testTimeSource.toGMTClock()

        val storage = AcceptAllCookiesStorage()
        val cookie = parseServerSetCookieHeader(
            "JSESSIONID=jc1wDGgCjR8s72-xdZYYZsLywZdCsiIT86U7X5h7.front10; HttpOnly"
        )
        storage.addCookie("http://localhost/", cookie)

        val plugin = HttpCookies(storage, emptyList())
        val builder = HttpRequestBuilder()

        plugin.captureHeaderCookies(builder)
        plugin.sendCookiesWith(builder, testClock.now())

        assertEquals(
            "JSESSIONID=jc1wDGgCjR8s72-xdZYYZsLywZdCsiIT86U7X5h7.front10",
            builder.headers[HttpHeaders.Cookie]
        )
    }

    @Test
    fun testRequestCookiesAreNotDroppedWhenEmptyStorage() = testSuspend {
        val testClock = testTimeSource.toGMTClock()
        val feature = HttpCookies(AcceptAllCookiesStorage(), emptyList())
        val builder = HttpRequestBuilder()

        builder.cookie("test", "value")
        feature.captureHeaderCookies(builder)
        feature.sendCookiesWith(builder, testClock.now())

        assertEquals("test=value", builder.headers[HttpHeaders.Cookie])
    }

    @Test
    fun testRequestCookiesArePreservedWhenAddingCookiesFromStorage() = testSuspend {
        val testClock = testTimeSource.toGMTClock()
        val storage = AcceptAllCookiesStorage()
        storage.addCookie("http://localhost/", parseServerSetCookieHeader("SOMECOOKIE=somevalue;"))
        val feature = HttpCookies(storage, emptyList())
        val builder = HttpRequestBuilder()

        builder.cookie("test", "value")
        feature.captureHeaderCookies(builder)
        feature.sendCookiesWith(builder, testClock.now())

        val renderedCookies = builder.headers[HttpHeaders.Cookie]!!.split(";")
        assertContains(renderedCookies, "test=value")
        assertContains(renderedCookies, "SOMECOOKIE=somevalue")
    }

    @Test
    fun testNoCookieHeaderWhenEmptyStorageAndNoRequestCookies() = testSuspend {
        val testClock = testTimeSource.toGMTClock()
        val feature = HttpCookies(AcceptAllCookiesStorage(), emptyList())
        val builder = HttpRequestBuilder()

        feature.captureHeaderCookies(builder)
        feature.sendCookiesWith(builder, testClock.now())

        assertNull(builder.headers[HttpHeaders.Cookie])
    }
}
