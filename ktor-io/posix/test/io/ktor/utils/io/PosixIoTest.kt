@file:OptIn(UnsafeNumber::class)

package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.test.*

class PosixIoTest {
    private val filename = "build/test.tmp"

    @Suppress("DEPRECATION")
    private lateinit var buffer: Buffer

    @Suppress("DEPRECATION")
    @BeforeTest
    fun setup() {
        buffer = Buffer(DefaultAllocator.alloc(4096))
        buffer.resetForWrite()
        buffer.writeFully("test".encodeToByteArray())

        unlink(filename)
    }

    @AfterTest
    fun cleanup() {
        DefaultAllocator.free(buffer.memory)
        unlink(filename)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Suppress("unused")
    internal fun Int.checkError(action: String = ""): Int = when {
        this < 0 -> memScoped { throw PosixException.forErrno(posixFunctionName = action) }
        else -> this
    }

    @OptIn(ExperimentalForeignApi::class)
    @Suppress("unused")
    internal fun Long.checkError(action: String = ""): Long = when {
        this < 0 -> memScoped { throw PosixException.forErrno(posixFunctionName = action) }
        else -> this
    }

    private val ZERO: size_t = 0u

    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    @Suppress("unused")
    internal fun size_t.checkError(action: String = ""): size_t = when (this) {
        ZERO -> errno.let { errno ->
            when (errno) {
                0 -> this
                else -> memScoped { throw PosixException.forErrno(posixFunctionName = action) }
            }
        }
        else -> this
    }
}
