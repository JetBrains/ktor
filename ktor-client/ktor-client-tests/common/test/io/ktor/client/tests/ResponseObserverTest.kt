/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class ResponseObserverTest : ClientLoader() {
    private var observerCalled = false

    @Test
    fun testEmptyResponseObserverIsNotFreezing() = clientTests {
        config {
            ResponseObserver {
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
        }
    }

    @Test
    fun testThrowInResponseObserverIsNotFailingRequest() = clientTests {
        config {
            ResponseObserver {
                error("fail")
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
        }
    }

    @Test
    fun testResponseObserverCalledWhenNoFilterPresent() = clientTests {
        config {
            install(ResponseObserver) {
                onResponse { observerCalled = true }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
            assertTrue { observerCalled }
        }
    }

    @Test
    fun testResponseObserverCalledWhenFilterMatched() = clientTests {
        config {
            install(ResponseObserver) {
                onResponse { observerCalled = true }
                filter { true }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
            assertTrue { observerCalled }
        }
    }

    @Test
    fun testResponseObserverNotCalledWhenFilterNotMatched() = clientTests {
        config {
            install(ResponseObserver) {
                onResponse { observerCalled = true }
                filter { false }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
            assertFalse { observerCalled }
        }
    }
}
