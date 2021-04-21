/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.http.*
import io.ktor.server.netty.http2.*
import io.netty.handler.codec.http2.*
import java.net.*
import kotlin.test.*

class NettyHttp2LocalConnectionPointTest {
    @Test
    fun testMethod() {
        val point = point {
            method("PUT")
        }

        assertEquals(HttpMethod.Put, point.method)
    }

    @Test
    fun testVersion() {
        val point = point {
            method("PUT")
        }

        assertEquals("HTTP/2", point.version)
    }

    @Test
    fun testScheme() {
        val point = point {
            scheme("https")
        }

        assertEquals("https", point.scheme)
    }

    @Test
    fun testSchemeMissing() {
        val point = point {
        }

        assertEquals("http", point.scheme)
    }

    @Test
    fun testPort() {
        val point = point {
            authority("host:443")
        }

        assertEquals(443, point.port)
    }

    @Test
    fun testPortUnspecified() {
        val point = point {
            authority("host")
        }

        assertEquals(80, point.port)
    }

    @Test
    fun testPortUnspecifiedWithAddress() {
        val point = point(localAddress = InetSocketAddress(8443)) {
            authority("host")
        }

        assertEquals(8443, point.port)
    }

    @Test
    fun testHost() {
        val point = point {
            authority("host1")
        }

        assertEquals("host1", point.host)
    }

    @Test
    fun testHostWithPort() {
        val point = point {
            authority("host1:80")
        }

        assertEquals("host1", point.host)
    }

    @Test
    fun testHostMissing() {
        val point = point {
        }

        assertEquals("localhost", point.host)
    }

    @Test
    fun testUri() {
        val point = point {
            path("/path/to/resource")
        }

        assertEquals("/path/to/resource", point.uri)
    }

    @Test
    fun testUriMissing() {
        val point = point {
        }

        assertEquals("/", point.uri)
    }

    @Test
    fun testRemoteAddress() {
        val address = InetSocketAddress.createUnresolved("some-host", 8554)
        val point = point(remoteAddress = address) {
        }

        assertEquals("some-host", point.remoteHost)
        assertTrue(address.isUnresolved)
    }

    @Test
    fun testRemoteAddressResolved() {
        val address = InetSocketAddress(
            Inet4Address.getByAddress("z", byteArrayOf(192.toByte(), 168.toByte(), 1, 1)),
            7777
        )
        val point = point(remoteAddress = address) {
        }

        assertEquals("z", point.remoteHost)
    }

    private fun headers(block: DefaultHttp2Headers.() -> Unit): Http2Headers {
        val headers = DefaultHttp2Headers()
        block(headers)
        return headers
    }

    private fun point(
        localAddress: InetSocketAddress? = null,
        remoteAddress: InetSocketAddress? = null,
        block: DefaultHttp2Headers.() -> Unit
    ): Http2LocalConnectionPoint {
        val headers = headers(block)
        return Http2LocalConnectionPoint(headers, localAddress, remoteAddress)
    }
}
