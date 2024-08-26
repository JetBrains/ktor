/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.util.network.*
import kotlin.test.*

class CIOConnectionPointTest {

    @Test
    fun testLocalHostAndPort() {
        val point = CIOConnectionPoint(
            remoteNetworkAddress = NetworkAddress("remote", 123),
            localNetworkAddress = NetworkAddress("local", 234),
            version = HttpProtocolVersion.HTTP_1_1.name,
            uri = "/",
            hostHeaderValue = "somehost:345",
            method = HttpMethod.Get
        )
        assertEquals("local", point.localHost)
        assertEquals(234, point.localPort)
    }

    @Test
    fun testServerHostAndPort() {
        val point = CIOConnectionPoint(
            remoteNetworkAddress = NetworkAddress("remote", 123),
            localNetworkAddress = NetworkAddress("local", 234),
            version = HttpProtocolVersion.HTTP_1_1.name,
            uri = "/",
            hostHeaderValue = "somehost:345",
            method = HttpMethod.Get
        )
        assertEquals("somehost", point.serverHost)
        assertEquals(345, point.serverPort)
    }

    @Test
    fun testServerHostAndPortNoHeader() {
        val point = CIOConnectionPoint(
            remoteNetworkAddress = NetworkAddress("remote", 123),
            localNetworkAddress = NetworkAddress("local", 234),
            version = HttpProtocolVersion.HTTP_1_1.name,
            uri = "/",
            hostHeaderValue = null,
            method = HttpMethod.Get
        )
        assertEquals("local", point.serverHost)
        assertEquals(234, point.serverPort)
    }
}
