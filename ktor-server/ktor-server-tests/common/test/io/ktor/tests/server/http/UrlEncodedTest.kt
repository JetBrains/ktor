/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlin.test.*

class UrlEncodedTest {
    suspend fun ApplicationRequest.parseUrlEncodedParameters(limit: Int = 1000): Parameters {
        return call.receive<String>().parseUrlEncodedParameters(contentCharset() ?: Charsets.UTF_8, limit)
    }

    @Test
    fun should_parse_simple_with_no_headers() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                val parsed = call.request.parseUrlEncodedParameters()
                assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"])
            }
        }
        client.get {
            setBody("field1=%D0%A2%D0%B5%D1%81%D1%82")
        }.bodyAsText()
    }

//    @Test
//    fun should_parse_simple_with_no_encoding() {
//        testApplication {
//            createCall {
//                setBody("field1=%D0%A2%D0%B5%D1%81%D1%82")
//                header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
//
//                val parsed = parseUrlEncodedParameters()
//                assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"])
//            }
//        }
//    }
//
//    @Test
//    fun should_parse_simple_with_specified_encoding_utf8() {
//        testApplication {
//            createCall {
//                setBody("field1=%D0%A2%D0%B5%D1%81%D1%82")
//                header(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=utf-8")
//
//                val parsed = parseUrlEncodedParameters()
//                assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"])
//            }
//        }
//    }
//
//    @Test
//    fun should_parse_simple_with_specified_encoding_non_utf() {
//        testApplication {
//            createCall {
//                setBody("field1=ḂḃĊċḊ")
//                header(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=ISO-8859-1")
//
//                val parsed = parseUrlEncodedParameters()
//                assertEquals("\u1E02\u1E03\u010A\u010B\u1E0A", parsed["field1"])
//            }
//        }
//    }
//
//    @Test
//    fun should_parse_simple_with_specified_encoding_non_utf_in_parameter() {
//        testApplication {
//            createCall {
//                setBody("field1=ḂḃĊċḊ&_charset_=ISO-8859-1")
//                header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
//
//                val parsed = parseUrlEncodedParameters()
//                assertEquals("\u1E02\u1E03\u010A\u010B\u1E0A", parsed["field1"])
//            }
//        }
//    }
//
//    @Test
//    fun testRenderUrlEncoded() {
//        assertEquals("p1=a+b", listOf("p1" to "a b").formUrlEncode())
//        assertEquals("p%3D1=a%3Db", listOf("p=1" to "a=b").formUrlEncode())
//        assertEquals("p1=a&p1=b&p2=c", listOf("p1" to "a", "p1" to "b", "p2" to "c").formUrlEncode())
//    }
//
//    @Test
//    fun testRenderUrlEncodedStringValues() {
//        assertEquals("p1=a+b", parametersOf("p1", listOf("a b")).formUrlEncode())
//        assertEquals("p%3D1=a%3Db", parametersOf("p=1", listOf("a=b")).formUrlEncode())
//        assertEquals("p1=a&p1=b&p2=c", parametersOf("p1" to listOf("a", "b"), "p2" to listOf("c")).formUrlEncode())
//    }
}
