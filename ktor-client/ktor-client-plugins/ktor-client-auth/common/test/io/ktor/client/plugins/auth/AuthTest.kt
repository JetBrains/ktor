/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.test.assertFailsWith

class AuthTest : ClientLoader() {

    @Test
    fun testDigestAuthLegacy() = clientTests(listOf("Js", "native")) {
        config {
            install(Auth) {
                digest {
                    credentials {
                        DigestAuthCredentials("MyName", "Circle Of Life")
                    }

                    realm = "testrealm@host.com"
                }
            }
        }
        test { client ->
            client.prepareGet("$TEST_SERVER/auth/digest").execute {
                assertTrue(it.status.isSuccess())
            }
        }
    }

    @Test
    fun testDigestAuth() = clientTests(listOf("Js", "native")) {
        config {
            install(Auth) {
                digest {
                    credentials { DigestAuthCredentials("MyName", "Circle Of Life") }
                    realm = "testrealm@host.com"
                }
            }
        }
        test { client ->
            client.get("$TEST_SERVER/auth/digest").let {
                assertTrue(it.status.isSuccess())
            }
        }
    }

    @Test
    fun testDigestAuthPerRealm() = clientTests(listOf("Js", "native")) {
        config {
            install(Auth) {
                digest {
                    credentials { DigestAuthCredentials("MyName", "Circle Of Life") }
                    realm = "testrealm@host.com"
                }
                digest {
                    credentials { DigestAuthCredentials("MyName", "some password") }
                    realm = "testrealm-2@host.com"
                }
            }
        }
        test { client ->
            client.get("$TEST_SERVER/auth/digest").let {
                assertTrue(it.status.isSuccess())
            }
            client.get("$TEST_SERVER/auth/digest-2").let {
                assertTrue(it.status.isSuccess())
            }
        }
    }

    @Test
    fun testDigestAuthSHA256() = clientTests(listOf("Js", "native")) {
        config {
            install(Auth) {
                digest {
                    algorithmName = "SHA-256"
                    credentials { DigestAuthCredentials("MyName", "Circle Of Life") }
                    realm = "testrealm@host.com"
                }
            }
        }
        test { client ->
            assertTrue(client.get("$TEST_SERVER/auth/digest-SHA256").status.isSuccess())
        }
    }

    @Suppress("DEPRECATION_ERROR")
    @Test
    fun testBasicAuthLegacy() = clientTests(listOf("Js")) {
        config {
            install(Auth) {
                basic {
                    username = "MyUser"
                    password = "1234"
                }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/auth/basic-fixed").body<String>()
        }
    }

    @Test
    fun testBasicAuth() = clientTests(listOf("Js")) {
        config {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("MyUser", "1234") }
                }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/auth/basic-fixed")
        }
    }

    @Test
    fun testAuthDoesntRefreshBeforeSend() = testWithEngine(MockEngine) {
        var refreshCount = 0
        config {
            install(Auth) {
                providers += object : AuthProvider {
                    @Deprecated("Please use sendWithoutRequest function instead")
                    override val sendWithoutRequest: Boolean = false
                    override fun isApplicable(auth: HttpAuthHeader): Boolean = true

                    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
                        request.headers.append(HttpHeaders.Authorization, "Auth1")
                    }

                    override suspend fun refreshToken(response: HttpResponse): Boolean {
                        refreshCount++
                        return true
                    }
                }
            }
            engine {
                addHandler { respond("ERROR", HttpStatusCode.Unauthorized) }
                addHandler { respond("OK", HttpStatusCode.OK) }
            }
        }

        test { client ->
            refreshCount = 0
            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, refreshCount)
        }
    }

    @Test
    fun testBasicAuthWithoutNegotiationLegacy() = clientTests {
        config {
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials("MyUser", "1234")
                    }

                    sendWithoutRequest { true }
                }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/auth/basic-fixed").body<String>()
        }
    }

    @Test
    fun testBasicAuthWithoutNegotiation() = clientTests {
        config {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("MyUser", "1234") }
                    sendWithoutRequest { true }
                }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/auth/basic-fixed")
        }
    }

    @Suppress("DEPRECATION_ERROR")
    @Test
    fun testUnauthorizedBasicAuthLegacy() = clientTests(listOf("Js")) {
        config {
            install(Auth) {
                basic {
                    username = "usr"
                    password = "pw"
                }
            }
            expectSuccess = false
        }

        test { client ->
            client.prepareGet("$TEST_SERVER/auth/unauthorized").execute { response ->
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun testUnauthorizedBasicAuth() = clientTests(listOf("Js")) {
        config {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("usr", "pw") }
                }
            }
            expectSuccess = false
        }

        test { client ->
            val response = client.get("$TEST_SERVER/auth/unauthorized")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun testBasicAuthMultiple() = clientTests(listOf("Js")) {
        config {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("MyUser", "1234") }
                    sendWithoutRequest { it.url.encodedPath.endsWith("basic-fixed") }
                }
                basic {
                    credentials { BasicAuthCredentials("user1", "Password1") }
                    sendWithoutRequest { it.url.encodedPath.endsWith("basic") }
                }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/auth/basic-fixed").bodyAsText()
            client.post("$TEST_SERVER/auth/basic") {
                setBody("{\"test\":\"text\"}")
            }.bodyAsText()
        }
    }

    @Test
    fun testBasicAuthMultipleNotSendWithoutRequest() = clientTests(listOf("Js")) {
        config {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("MyUser", "1234") }
                    realm = "Ktor Server"
                }
                basic {
                    credentials { BasicAuthCredentials("user1", "Password1") }
                    realm = "my-server"
                }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/auth/basic-fixed").bodyAsText()
            client.post("$TEST_SERVER/auth/basic") {
                setBody("{\"test\":\"text\"}")
            }.bodyAsText()
        }
    }

    @Test
    fun testBasicAuthPerRealm() = clientTests(listOf("Js")) {
        config {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("MyUser", "1234") }
                    realm = "Ktor Server"
                }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/auth/basic-fixed")
            client.post("$TEST_SERVER/auth/basic") { expectSuccess = false }.let {
                assertEquals(HttpStatusCode.Unauthorized, it.status)
            }
        }
    }

    @Test
    fun testUnauthorizedBearerAuthWithInvalidAccessAndRefreshTokensAsNulls() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { null }
                    loadTokens { null }
                }
            }

            expectSuccess = false
        }

        test { client ->
            client.prepareGet("$TEST_SERVER/auth/bearer/test-refresh").execute {
                assertEquals(HttpStatusCode.Unauthorized, it.status)
            }
        }
    }

    @Test
    fun testUsesFreshTokenIfAvailable() = testSuspend {
        val request1FinishMonitor = Job()
        val request2StartMonitor = Job()
        var refreshCount = 0
        val client = HttpClient(MockEngine) {
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens("initial", "initial")
                    }

                    refreshTokens {
                        val tokens = BearerTokens("new$refreshCount", "new$refreshCount")
                        refreshCount++
                        tokens
                    }
                }
            }

            engine {
                addHandler { request ->
                    fun respond(): HttpResponseData {
                        return if (request.headers[HttpHeaders.Authorization] != "Bearer initial") {
                            respond("OK")
                        } else {
                            respond("Error", HttpStatusCode.Unauthorized, headersOf("WWW-Authenticate", "Bearer"))
                        }
                    }

                    when (request.url.encodedPath) {
                        "/url1" -> {
                            request2StartMonitor.join()
                            respond()
                        }

                        "/url2" -> {
                            request1FinishMonitor.join()
                            respond()
                        }

                        else -> throw IllegalStateException()
                    }
                }
            }
        }

        val request1 = launch {
            client.get("/url1")
            request1FinishMonitor.complete()
        }
        val request2 = launch {
            request2StartMonitor.complete()
            client.get("/url2")
        }

        request1.join()
        request2.join()
        assertEquals(1, refreshCount)
    }

    @Test
    fun testUnauthorizedBearerAuthWithInvalidAccessAndRefreshTokens() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { BearerTokens("invalid", "refresh") }
                    loadTokens { BearerTokens("invalid", "refresh") }
                }
            }

            expectSuccess = false
        }

        test { client ->
            client.prepareGet("$TEST_SERVER/auth/bearer/test-refresh").execute {
                assertEquals(HttpStatusCode.Unauthorized, it.status)
            }
        }
    }

    // The return of refreshTokenFun is null, cause it should not be called at all, if loadTokensFun returns valid tokens
    @Test
    fun testUnauthorizedBearerAuthWithValidAccessTokenAndInvalidRefreshToken() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { null }
                    loadTokens { BearerTokens("valid", "refresh") }
                }
            }
        }

        test { client ->
            client.prepareGet("$TEST_SERVER/auth/bearer/test-refresh").execute {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
    }

    @Test
    fun testUnauthorizedBearerAuthWithInvalidAccessTokenAndValidRefreshToken() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { BearerTokens("valid", "refresh") }
                    loadTokens { BearerTokens("invalid", "refresh") }
                    realm = "TestServer"
                }
            }
        }

        test { client ->
            client.prepareGet("$TEST_SERVER/auth/bearer/test-refresh").execute {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
    }

    @Test
    fun testUnauthorizedRefreshTokenWithoutWWWAuthenticateHeaderIfOneProviderIsInstalled() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { BearerTokens("valid", "refresh") }
                    loadTokens { BearerTokens("invalid", "refresh") }
                    realm = "TestServer"
                }
            }
        }

        test { client ->
            client.prepareGet("$TEST_SERVER/auth/bearer/test-refresh-no-www-authenticate-header").execute {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
    }

    @Test
    fun testUnauthorizedDoesNotRefreshTokenWithoutWWWAuthenticateHeaderIfMultipleProvidersAreInstalled() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { BearerTokens("valid", "refresh") }
                    loadTokens { BearerTokens("invalid", "refresh") }
                    realm = "TestServer"
                }
                basic {
                    credentials { BasicAuthCredentials("name", "password") }
                }
            }
        }

        test { client ->
            client.prepareGet("$TEST_SERVER/auth/bearer/test-refresh-no-www-authenticate-header").execute {
                assertEquals(HttpStatusCode.Unauthorized, it.status)
            }
        }
    }

    @Test
    fun testRefreshOnBackgroundThread() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { BearerTokens("valid", "refresh") }
                    loadTokens { BearerTokens("invalid", "refresh") }
                    realm = "TestServer"
                }
            }
        }

        test { client ->
            val response = withContext(Dispatchers.Default) {
                client.get("$TEST_SERVER/auth/bearer/test-refresh")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Suppress("JoinDeclarationAndAssignment")
    @Test
    fun testRefreshWithSameClient() = clientTests {
        lateinit var clientWithAuth: HttpClient
        test { client ->
            clientWithAuth = client.config {
                install(Auth) {
                    bearer {
                        loadTokens { BearerTokens("first", "first") }

                        refreshTokens {
                            val token = clientWithAuth.get("$TEST_SERVER/auth/bearer/token/second").bodyAsText()
                            BearerTokens(token, token)
                        }
                    }
                }
            }

            val first = clientWithAuth.get("$TEST_SERVER/auth/bearer/first").bodyAsText()
            val second = clientWithAuth.get("$TEST_SERVER/auth/bearer/second").bodyAsText()

            assertEquals("OK", first)
            assertEquals("OK", second)
        }
    }

    @Suppress("JoinDeclarationAndAssignment")
    @Test
    fun testRefreshReplies401() = clientTests {
        lateinit var clientWithAuth: HttpClient
        test { client ->
            clientWithAuth = client.config {
                install(Auth) {
                    bearer {
                        loadTokens { BearerTokens("first", "first") }

                        refreshTokens {
                            val token = clientWithAuth.get("$TEST_SERVER/auth/bearer/token/refresh-401") {
                                markAsRefreshTokenRequest()
                            }.bodyAsText()
                            BearerTokens(token, token)
                        }
                    }
                }
            }

            val result = clientWithAuth.get("$TEST_SERVER/auth/bearer/second")
            assertEquals(HttpStatusCode.Unauthorized, result.status)
        }
    }

    @Test
    fun testRefreshWithSameClientInBlock() = clientTests {
        config {
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("first", "first") }

                    refreshTokens {
                        val token = client.get("$TEST_SERVER/auth/bearer/token/second").bodyAsText()
                        BearerTokens(token, token)
                    }
                }
            }
        }
        test { client ->
            val first = client.get("$TEST_SERVER/auth/bearer/first").bodyAsText()
            val second = client.get("$TEST_SERVER/auth/bearer/second").bodyAsText()

            assertEquals("OK", first)
            assertEquals("OK", second)
        }
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testMultipleRefreshShouldMakeSingleCall() = clientTests {
        var refreshRequestsCount = 0
        config {
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("first", "first") }

                    refreshTokens {
                        refreshRequestsCount++
                        val token = client.get("$TEST_SERVER/auth/bearer/token/second?delay=500").bodyAsText()
                        BearerTokens(token, token)
                    }
                }
            }
        }
        test { client ->
            refreshRequestsCount = 0
            client.get("$TEST_SERVER/auth/bearer/first").bodyAsText()

            val jobs = mutableListOf<Job>()
            jobs += GlobalScope.launch {
                val second = client.get("$TEST_SERVER/auth/bearer/second").bodyAsText()
                assertEquals("OK", second)
            }
            jobs += GlobalScope.launch {
                val second = client.get("$TEST_SERVER/auth/bearer/second").bodyAsText()
                assertEquals("OK", second)
            }
            jobs += GlobalScope.launch {
                val second = client.get("$TEST_SERVER/auth/bearer/second").bodyAsText()
                assertEquals("OK", second)
            }
            jobs.joinAll()
            assertEquals(1, refreshRequestsCount)
        }
    }

    @Test
    fun testRefreshAfterException() = clientTests {
        var firstCall = true
        config {
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("first", "first") }

                    refreshTokens {
                        if (firstCall) {
                            firstCall = false
                            throw IOException("Refresh failed")
                        }
                        val token = client.get("$TEST_SERVER/auth/bearer/token/second?delay=500").bodyAsText()
                        BearerTokens(token, token)
                    }
                }
            }
        }
        test { client ->
            firstCall = true
            val first = client.get("$TEST_SERVER/auth/bearer/first").bodyAsText()
            assertEquals("OK", first)

            val error = assertFailsWith<IOException> {
                client.get("$TEST_SERVER/auth/bearer/second")
            }
            assertEquals("Refresh failed", error.message)

            val second = client.get("$TEST_SERVER/auth/bearer/second").bodyAsText()
            assertEquals("OK", second)
        }
    }

    private var loadCount = 0

    @Test
    fun testLoadTokenAfterClear() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokens { null }
                    loadTokens {
                        loadCount++
                        BearerTokens("valid", "refresh")
                    }
                }
            }
        }

        test { client ->
            loadCount = 0
            client.get("$TEST_SERVER/auth/bearer/test-refresh")
                .bodyAsText()
            client.authProviders.filterIsInstance<BearerAuthProvider>().first().clearToken()
            client.get("$TEST_SERVER/auth/bearer/test-refresh")
                .bodyAsText()

            assertEquals(2, loadCount)
        }
    }

    @Test
    fun testMultipleChallengesInHeader() = clientTests {
        config {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("Invalid", "Invalid") }
                }
                bearer {
                    loadTokens { BearerTokens("test", "test") }
                }
            }
        }
        test { client ->
            val responseOneHeader = client.get("$TEST_SERVER/auth/multiple/header").bodyAsText()
            assertEquals("OK", responseOneHeader)
        }
    }

    @Test
    fun testMultipleChallengesInHeaders() = clientTests {
        config {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("Invalid", "Invalid") }
                }
                bearer {
                    loadTokens { BearerTokens("test", "test") }
                }
            }
        }
        test { client ->
            val responseMultipleHeaders = client.get("$TEST_SERVER/auth/multiple/headers").bodyAsText()
            assertEquals("OK", responseMultipleHeaders)
        }
    }

    @Test
    fun testMultipleChallengesInHeaderUnauthorized() = clientTests {
        test { client ->
            val response = client.get("$TEST_SERVER/auth/multiple/header")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            response.headers[HttpHeaders.WWWAuthenticate]?.also {
                assertTrue { it.contains("Bearer") }
                assertTrue { it.contains("Basic") }
                assertTrue { it.contains("Digest") }
            } ?: run {
                fail("Expected WWWAuthenticate header")
            }
        }
    }

    @Test
    fun testMultipleChallengesInMultipleHeadersUnauthorized() = clientTests(listOf("Js")) {
        test { client ->
            val response = client.get("$TEST_SERVER/auth/multiple/headers")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            response.headers.getAll(HttpHeaders.WWWAuthenticate)?.let {
                assertEquals(2, it.size)
                it.joinToString().let { header ->
                    assertTrue { header.contains("Basic") }
                    assertTrue { header.contains("Digest") }
                    assertTrue { header.contains("Bearer") }
                }
            } ?: run {
                fail("Expected WWWAuthenticate header")
            }
        }
    }
}
