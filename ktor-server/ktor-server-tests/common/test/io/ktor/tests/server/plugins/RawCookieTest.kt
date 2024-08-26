/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import kotlin.test.*

class RawCookieTest {
    @Test
    fun testRawEncodingWithEquals() {
        val cookieValue = "my.value.key=my.value.value+my.value.id=5"
        val encoded = encodeCookieValue(cookieValue, CookieEncoding.RAW)
        assertEquals(cookieValue, encoded)
    }
}
