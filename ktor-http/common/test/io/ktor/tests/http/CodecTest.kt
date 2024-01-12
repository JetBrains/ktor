/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.random.*
import kotlin.test.*

class CodecTest {
    private val swissAndGerman = "\u0047\u0072\u00fc\u0065\u007a\u0069\u005f\u007a\u00e4\u006d\u00e4"
    private val russian = "\u0412\u0441\u0435\u043c\u005f\u043f\u0440\u0438\u0432\u0435\u0442"
    private val urlPath = "/wikipedia/commons/9/9c/University_of_Illinois_at_Urbana\u2013Champaign_logo.svg"
    private val surrogateSymbolUrlPath = "/path/🐕"

    @Test /*(timeout = 1000L)*/
    @Ignore
    fun testDecodeRandom() {
        val chars = "+%0123abc"

        for (step in 0..1000) {
            val size = Random.nextInt(15) + 1
            val sb = CharArray(size)

            for (i in 0 until size) {
                sb[i] = chars[Random.nextInt(chars.length)]
            }

            try {
                sb.concatToString().decodeURLQueryComponent()
            } catch (ignore: URLDecodeException) {
            } catch (t: Throwable) {
                fail("Failed at ${sb.concatToString()} with: $t")
            }
        }
    }

    @Test
    fun testUTFEncodeDecode() {
        assertEquals("%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", russian.encodeURLQueryComponent())
        assertEquals(
            "%D0%92%D1%81%D0%B5%D0%BC%5F%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82",
            russian.encodeURLQueryComponent(encodeFull = true)
        )

        assertEquals("Gr%C3%BCezi_z%C3%A4m%C3%A4", swissAndGerman.encodeURLQueryComponent())
        assertEquals("Gr%C3%BCezi%5Fz%C3%A4m%C3%A4", swissAndGerman.encodeURLQueryComponent(encodeFull = true))

        for (msg in listOf(russian, swissAndGerman)) {
            encodeAndDecodeTest(msg)
        }
    }

    @Test
    fun testBasicEncodeDecode() {
        val test = "Test me!"
        assertEquals("Test%20me!", test.encodeURLQueryComponent())
        assertEquals("Test%20me%21", test.encodeURLParameter())
        encodeAndDecodeTest(test)
    }

    @Test
    fun testEncodeURLPathPreservesPercentEncoding() {
        val test = "/a/path/with/a%20space/"
        assertEquals(test, test.encodeURLPath(encodeEncoded = false))
        assertEquals(test.replace("%", "%25"), test.encodeURLPath())
    }

    @Test
    fun testEncodeURLPathPreservesValidPartsAndSlashes() {
        val URL_ALPHABET = (('a'..'z') + ('A'..'Z') + ('0'..'9'))
        val VALID_PATH_PART = listOf(
            ':', '@',
            '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
            '-', '.', '_', '~'
        )
        val preservedSymbols = listOf(URL_ALPHABET, VALID_PATH_PART, listOf("/")).flatten().joinToString("")
        val test = "/a/path/$preservedSymbols/"

        assertEquals(test, test.encodeURLPath())
    }

    @Test
    fun testSimpleBasicEncodeDecode() {
        val s = "simple"
        val encoded = s.encodeURLQueryComponent()

        assertEquals("simple", encoded)
        encodeAndDecodeTest(s)
    }

    @Test
    fun testBasicEncodeDecodeURLPart() {
        val s = "Test me!"
        val encoded = s.encodeURLParameter()

        assertEquals("Test%20me%21", encoded)
        encodeAndDecodeTest(s)
    }

    @Test
    fun testAllReserved() {
        val text = "*~!@#$%^&()+{}\"\\;:`,/[]"
        assertEquals("*~!@#$%25%5E&()+%7B%7D%22%5C;:%60,/[]", text.encodeURLQueryComponent())
        assertEquals(
            "%2A%7E%21%40%23%24%25%5E%26%28%29%2B%7B%7D%22%5C%3B%3A%60%2C%2F%5B%5D",
            text.encodeURLQueryComponent(encodeFull = true)
        )

        encodeAndDecodeTest(text)
    }

    @Test
    fun testBrokenOrIncompleteHEX() {
        assertFails {
            "foo+%+bar".decodeURLQueryComponent()
        }
        assertEquals("0", "%30".decodeURLQueryComponent())
        assertFails {
            "%".decodeURLQueryComponent()
        }
        assertFails {
            "%0".decodeURLQueryComponent()
        }
    }

    @Test
    fun testEncodeURLPathUTF() {
        assertEquals(
            "/wikipedia/commons/9/9c/University_of_Illinois_at_Urbana%E2%80%93Champaign_logo.svg",
            urlPath.encodeURLPath()
        )
        assertEquals("%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", russian.encodeURLPath())
        assertEquals("Gr%C3%BCezi_z%C3%A4m%C3%A4", swissAndGerman.encodeURLPath())
    }

    @Test
    fun testFormUrlEncode() {
        val result = StringBuilder()

        mapOf(
            "a" to listOf("b", "c", "d"),
            "1" to listOf("2"),
            "x" to listOf("y", "z")
        ).entries.formUrlEncodeTo(result)

        assertEquals("a=b&a=c&a=d&1=2&x=y&x=z", result.toString())
    }

    @Test
    fun testEncodeURLPathSurrogateSymbol() {
        assertEquals("/path/%F0%9F%90%95", surrogateSymbolUrlPath.encodeURLPath())
    }

    private fun encodeAndDecodeTest(text: String) {
        val encode1 = text.encodeURLQueryComponent()
        val decode1 = encode1.decodeURLQueryComponent()
        assertEquals(text, decode1)
        val encode2 = text.encodeURLParameter()
        val decode2 = encode2.decodeURLPart()
        assertEquals(text, decode2)
    }
}
