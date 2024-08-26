/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.core.*
import kotlinx.io.*
import java.security.*
import kotlin.random.*
import kotlin.test.*

class HashFunctionConsistencyTest {
    @Test
    fun sha1() {
        test("SHA-1", ::Sha1)
    }

    private fun test(algorithm: String, newHashFunction: () -> HashFunction) {
        for (seed in 0L until 1000L) {
            for (updateCount in 0 until 10) {
                test(
                    algorithm = algorithm,
                    hashFunction = newHashFunction(),
                    seed = seed,
                    updateCount = updateCount
                )
            }
        }
    }

    private fun test(
        algorithm: String,
        hashFunction: HashFunction,
        seed: Long,
        updateCount: Int
    ) {
        val byteArray = buildPacket {
            val random = Random(seed)
            for (i in 0 until updateCount) {
                val size = random.nextInt(1000) + 1 // size must be >= 1.
                val byteArray = ByteArray(size).also { random.nextBytes(it) }
                val offset = random.nextInt(size)
                val length = random.nextInt(size - offset)

                hashFunction.update(byteArray, offset, length)
                writeFully(byteArray, offset, length)
            }
        }.readByteArray()

        val jdkHash = MessageDigest.getInstance(algorithm).digest(byteArray)

        assertContentEquals(jdkHash, hashFunction.digest())
        assertContentEquals(jdkHash, hashFunction.digest(byteArray))
    }
}
