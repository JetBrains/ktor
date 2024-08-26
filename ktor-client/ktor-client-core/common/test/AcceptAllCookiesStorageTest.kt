/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.date.*
import kotlin.test.*

class AcceptAllCookiesStorageTest {

    @Test
    fun testStorageUsesMaxAge() = testSuspend {
        var time = 1L
        val storage = AcceptAllCookiesStorage { time }
        val cookie = Cookie("name", "value", maxAge = 1)
        storage.addCookie(Url("http://localhost/"), cookie)

        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(emptyList(), storage.get(Url("http://localhost/")))
    }

    @Test
    fun testStorageUsesExpires() = testSuspend {
        var time = 1L
        val storage = AcceptAllCookiesStorage { time }
        val cookie = Cookie("name", "value", expires = GMTDate(1001))
        storage.addCookie(Url("http://localhost/"), cookie)

        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(emptyList(), storage.get(Url("http://localhost/")))
    }

    @Test
    fun testStoragePrefersMaxAgeOverExpires() = testSuspend {
        var time = 1L
        val storage = AcceptAllCookiesStorage { time }
        val cookie = Cookie("name", "value", expires = GMTDate(1001), maxAge = 2)
        storage.addCookie(Url("http://localhost/"), cookie)

        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(emptyList(), storage.get(Url("http://localhost/")))
    }

    @Test
    fun testLongMaxAge() = testSuspend {
        val storage = AcceptAllCookiesStorage()
        val twoYears = 2 * 365 * 24 * 3600
        val cookie = Cookie("name", "value", maxAge = twoYears)
        storage.addCookie(Url("/"), cookie)

        assertEquals(cookie.value, storage.get(Url("/")).single().value)
    }
}
