import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.engine.darwin.internal.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.websocket.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.Foundation.NSHTTPCookieStorage.Companion.sharedHTTPCookieStorage
import kotlin.test.*

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class DarwinEngineTest {

    @Test
    fun testRequestInRunBlockingDispatchersDefault() = runBlocking(Dispatchers.Default) {
        val client = HttpClient(Darwin)

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
    fun testRequestInRunBlockingDispatchersUnconfined() = runBlocking(Dispatchers.Unconfined) {
        val client = HttpClient(Darwin)

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
    fun testRequestInRunBlockingDispatchersNone() = runBlocking {
        val client = HttpClient(Darwin)

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
    fun testQueryWithCyrillic() = runBlocking {
        val client = HttpClient(Darwin)

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
    fun testQueryWithMultipleParams() = runBlocking {
        val client = HttpClient(Darwin)

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
    fun testCookieIsNotPersistedByDefault() = runBlocking {
        val client = HttpClient(Darwin)
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
    fun testCookiePersistedWithSessionStore() = runBlocking {
        val client = HttpClient(Darwin) {
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
    fun testOverrideDefaultSession(): Unit = runBlocking {
        val client = HttpClient(Darwin) {
            val delegate = KtorNSURLSessionDelegate()
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
    fun testOverrideDefaultSessionWithWebSockets(): Unit = runBlocking {
        val client = HttpClient(Darwin) {
            val delegate = KtorNSURLSessionDelegate()
            val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration(),
                delegate,
                delegateQueue = NSOperationQueue()
            )
            engine {
                usePreconfiguredSession(session, delegate)
            }
            install(WebSockets)
        }

        try {
            val session = client.webSocketSession("$TEST_WEBSOCKET_SERVER/websockets/echo")
            session.send("test")
            val response = session.incoming.receive() as Frame.Text
            assertEquals("test", response.readText())
            session.close()
        } finally {
            client.close()
        }
    }

    @Test
    fun testConfigureRequest(): Unit = runBlocking {
        val client = HttpClient(Darwin) {
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

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
    @Test
    fun testConfigureWebsocketRequest(): Unit = runBlocking {
        var customChallengeCalled = false
        val client = HttpClient(Darwin) {
            engine {
                handleChallenge { _, _, challenge, completionHandler ->
                    customChallengeCalled = true
                    challenge.protectionSpace.serverTrust?.let {
                        if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
                            val credential = NSURLCredential.credentialForTrust(it)
                            completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                        }
                    }
                }
            }

            install(WebSockets)
        }

        val session = client.webSocketSession("wss://127.0.0.1:8089/websockets/echo")
        session.send("test")
        val response = session.incoming.receive() as Frame.Text
        assertEquals("test", response.readText())
        assertTrue(customChallengeCalled)
        session.close()
    }

    @Test
    fun testWebSocketPingInterval() = testSuspend {
        val client = HttpClient(Darwin) {
            install(WebSockets) {
                pingInterval = 1000
            }
        }

        assertFailsWith<TimeoutCancellationException> {
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                withTimeout(5000) {
                    for (frame in incoming) {
                    }
                }
            }
        }
    }

    private fun stringToNSUrlString(value: String): String {
        return Url(value).toNSUrl().absoluteString!!
    }
}
