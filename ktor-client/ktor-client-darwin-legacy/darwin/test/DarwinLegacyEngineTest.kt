
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.engine.darwin.internal.legacy.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.Foundation.NSHTTPCookieStorage.Companion.sharedHTTPCookieStorage
import kotlin.coroutines.*
import kotlin.test.*

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class DarwinLegacyEngineTest {

    val testCoroutineContext: CoroutineContext = Dispatchers.Default

    @Test
    fun testRequestInRunBlocking() = runBlocking(testCoroutineContext) {
        val client = HttpClient(DarwinLegacy)

        try {
            withTimeout(1000) {
                val response = client.get(TEST_SERVER)
                assertEquals("Hello, world!", response.bodyAsText())
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun testQueryWithCyrillic() = runBlocking(testCoroutineContext) {
        val client = HttpClient(DarwinLegacy)

        try {
            withTimeout(1000) {
                val response = client.get("$TEST_SERVER/echo_query?привет")
                assertEquals("привет=[]", response.bodyAsText())
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun testQueryWithMultipleParams() = runBlocking(testCoroutineContext) {
        val client = HttpClient(DarwinLegacy)

        try {
            withTimeout(1000) {
                val response = client.get("$TEST_SERVER/echo_query?asd=qwe&asd=123&qwe&zxc=vbn")
                assertEquals("asd=[qwe, 123], qwe=[], zxc=[vbn]", response.bodyAsText())
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun testNSUrlSanitize() {
        assertEquals(
            "http://127.0.0.1/echo_query?%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82",
            stringToNSUrlString("http://127.0.0.1/echo_query?привет")
        )

        val possibleResults = setOf(
            "http://%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82.%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82/",
            "http://xn--b1agh1afp.xn--b1agh1afp/",
        )
        assertTrue(
            stringToNSUrlString("http://привет.привет/") in possibleResults
        )
    }

    @Test
    fun testCookieIsNotPersistedByDefault() = runBlocking(testCoroutineContext) {
        val client = HttpClient(DarwinLegacy)
        try {
            client.get("$TEST_SERVER/cookies")
            val result = client.get("$TEST_SERVER/cookies/dump")
                .bodyAsText()

            assertEquals("Cookies: ", result)
        } finally {
            client.close()
        }
    }

    @Test
    fun testCookiePersistedWithSessionStore() = runBlocking(testCoroutineContext) {
        val client = HttpClient(DarwinLegacy) {
            engine {
                configureSession {
                    setHTTPCookieStorage(sharedHTTPCookieStorage)
                }
            }
        }

        try {
            client.get("$TEST_SERVER/cookies")
            val result = client.get("$TEST_SERVER/cookies/dump")
                .bodyAsText()

            assertEquals("Cookies: hello-cookie=my-awesome-value", result)
        } finally {
            client.close()
        }
    }

    @Test
    fun testOverrideDefaultSession(): Unit = runBlocking(testCoroutineContext) {
        val client = HttpClient(DarwinLegacy) {
            val delegate = KtorLegacyNSURLSessionDelegate()
            val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration(),
                delegate,
                delegateQueue = NSOperationQueue()
            )
            engine {
                usePreconfiguredSession(session, delegate)
            }
        }

        try {
            val response = client.get(TEST_SERVER)
            assertEquals("Hello, world!", response.bodyAsText())
        } finally {
            client.close()
        }
    }

    @Test
    fun testConfigureRequest(): Unit = runBlocking(testCoroutineContext) {
        val client = HttpClient(DarwinLegacy) {
            engine {
                configureRequest {
                    setValue("my header value", forHTTPHeaderField = "XCustomHeader")
                }
            }
        }

        val response = client.get("$TEST_SERVER/headers/echo?headerName=XCustomHeader")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("my header value", response.bodyAsText())
    }

    private fun stringToNSUrlString(value: String): String {
        return Url(value).toNSUrl().absoluteString!!
    }
}
