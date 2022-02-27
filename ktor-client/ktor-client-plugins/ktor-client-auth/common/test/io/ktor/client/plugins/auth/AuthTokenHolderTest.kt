/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlin.test.*

class AuthTokenHolderTest {

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testSetTokenCalledOnce() = testSuspend {
        val holder = AuthTokenHolder<BearerTokens> { TODO() }

        val monitor = Job()
        var firstExecuted = false
        var secondExecuted = false
        val first = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                firstExecuted = true
                monitor.join()
                BearerTokens("1", "2")
            }
        }

        val second = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                secondExecuted = true
                BearerTokens("1", "2")
            }
        }

        monitor.complete()
        first.join()
        second.join()

        assertTrue(firstExecuted)
        assertFalse(secondExecuted)
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testLoadTokenWaitsUntilTokenIsLoaded() = testSuspend {
        val monitor = Job()
        val holder = AuthTokenHolder {
            monitor.join()
            BearerTokens("1", "2")
        }

        val first = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        val second = GlobalScope.async(Dispatchers.Unconfined) {
            holder.loadToken()
        }

        monitor.complete()
        assertNotNull(first.await())
        assertNotNull(second.await())
    }
}
