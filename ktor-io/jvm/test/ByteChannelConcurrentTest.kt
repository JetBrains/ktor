/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class ByteChannelConcurrentTest {
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReadAndWriteConcurrentWithCopyTo() = runBlocking {
        repeat(100000) {
            val result = ByteChannel()

            val writer = GlobalScope.reader {
                channel.copyAndClose(result)
            }.channel

            val content = byteArrayOf(1, 2)
            writer.writeByteArray(content)
            writer.flushAndClose()

            assertContentEquals(content, result.readByteArray(2))
        }
    }
}
