/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import kotlinx.cinterop.*
import kotlin.test.*

class ByteChannelNativeTests : ByteChannelTestBase(true) {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testCPointersReadWriteFully() = runTest {
        val array = ByteArray(4)
        for (i in 0..3) {
            array[i] = i.toByte()
        }

        array.usePinned { pinned ->
            val ptr = pinned.addressOf(0)
            ch.writeFully(ptr, 0, 4)
        }

        val result = ByteArray(array.size)
        result.usePinned { pinned ->
            val ptr = pinned.addressOf(0)
            ch.readFully(ptr, 0, 4)
        }

        assertTrue { array.contentEquals(result) }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testCPointersReadWriteFullyWithShift() = runTest {
        for (shift in 0..3) {
            val array = ByteArray(4)
            for (i in 0..3) {
                array[i] = i.toByte()
            }

            array.usePinned { pinned ->
                val ptr = pinned.addressOf(0)
                ch.writeFully(ptr, shift, 4 - shift)
            }

            val result = ByteArray(array.size)
            result.usePinned { pinned ->
                val ptr = pinned.addressOf(0)
                ch.readFully(ptr, shift, 4 - shift)
            }

            for (i in shift..3) {
                assertEquals(array[i], result[i])
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testCPointersReadAvailable() = runTest {
        val array = ByteArray(4)
        for (i in 0..3) {
            array[i] = i.toByte()
        }

        array.usePinned { pinned ->
            val ptr = pinned.addressOf(0)
            ch.writeFully(ptr, 0, 4)
        }

        val result = ByteArray(array.size + 10)
        val size = result.usePinned { pinned ->
            val ptr = pinned.addressOf(0)
            ch.readAvailable(ptr, 0, result.size)
        }

        assertTrue { array.contentEquals(result.copyOf(size)) }
    }
}
