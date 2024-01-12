/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.test.*

@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
class MultiWorkerDispatcherTest {

    private val dispatcher = Dispatchers.createFixedThreadDispatcher(
        name = "CLIENT TEST DISPATCHER",
        threads = 4
    )

    @Test
    fun testClientDispatcherClose() {
        dispatcher.close()
    }

    @Test
    fun testCloseWithSuspendedThread() {
        GlobalScope.launch(dispatcher) {
            suspendCoroutineUninterceptedOrReturn {
            }
        }

        dispatcher.close()
    }

    @Test
    fun testCloseBlockedThreadWithSuspensionPoint() {
        GlobalScope.launch(dispatcher) {
            while (true) {
                yield()
            }
        }

        dispatcher.close()
    }

    @Test
    fun testCloseAfterTask() {
        val job = GlobalScope.launch(dispatcher) {
            delay(100)
        }

        runBlocking {
            job.join()
        }

        dispatcher.close()
    }
}
