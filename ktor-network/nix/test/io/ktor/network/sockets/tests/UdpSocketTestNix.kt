/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.test.*

class UdpSocketTestNix {

    @Test
    fun testDescriptorClose() = testSuspend {
        val selector = SelectorManager()
        val socket = aSocket(selector)
            .udp()
            .bind(InetSocketAddress("127.0.0.1", 8002))
        val descriptor = (socket as DatagramSocketNative).selectable.descriptor

        socket.close()
        selector.close()

        selector.coroutineContext[Job]?.join()

        val isDescriptorValid = fcntl(descriptor, F_GETFL) != -1 || errno != EBADF
        check(!isDescriptorValid) { "Descriptor was not closed" }
    }
}
