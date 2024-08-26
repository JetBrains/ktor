/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

class OAuth2Test {

    private fun ApplicationTestBuilder.noRedirectsClient() = createClient { followRedirects = false }

    private val DefaultSettings = OAuthServerSettings.OAuth2ServerSettings(
        name = "oauth2",
        authorizeUrl = "https://login-server-com/authorize",
        accessTokenUrl = "https://login-server-com/oauth/access_token",
        clientId = "clientId1",
        clientSecret = "clientSecret1"
    )

    private val DefaultSettingsWithScopes = OAuthServerSettings.OAuth2ServerSettings(
        name = "oauth2",
        authorizeUrl = "https://login-server-com/authorize",
        accessTokenUrl = "https://login-server-com/oauth/access_token",
        clientId = "clientId1",
        clientSecret = "clientSecret1",
        defaultScopes = listOf("http://example.com/scope1", "http://example.com/scope2")
    )

    private val DefaultSettingsWithExtraParameters = OAuthServerSettings.OAuth2ServerSettings(
        name = "oauth2",
        authorizeUrl = "https://login-server-com/authorize",
        accessTokenUrl = "https://login-server-com/oauth/access_token",
        clientId = "clientId1",
        clientSecret = "clientSecret1",
        extraAuthParameters = listOf("a" to "a1", "a" to "a2", "b" to "b1"),
    )

    private val DefaultSettingsWithInterceptor = OAuthServerSettings.OAuth2ServerSettings(
        name = "oauth2",
        authorizeUrl = "https://login-server-com/authorize",
        accessTokenUrl = "https://login-server-com/oauth/access_token",
        clientId = "clientId1",
        clientSecret = "clientSecret1",
        authorizeUrlInterceptor = {
            parameters.append("custom", "value1")
        }
    )

    private val DefaultSettingsWithMethodPost = OAuthServerSettings.OAuth2ServerSettings(
        name = "oauth2",
        authorizeUrl = "https://login-server-com/authorize",
        accessTokenUrl = "https://login-server-com/oauth/access_token",
        clientId = "clientId1",
        clientSecret = "clientSecret1",
        requestMethod = HttpMethod.Post
    )

    private val testClient = createOAuth2Server(
        object : OAuth2Server {
            override fun requestToken(
                clientId: String,
                clientSecret: String,
                grantType: String,
                state: String?,
                code: String?,
                redirectUri: String?,
                userName: String?,
                password: String?
            ): OAuthAccessTokenResponse.OAuth2 {
                if (clientId != "clientId1") {
                    throw OAuth2Exception.InvalidGrant("Wrong clientId $clientId")
                }
                if (clientSecret != "clientSecret1") {
                    throw OAuth2Exception.InvalidGrant("Wrong client secret $clientSecret")
                }
                when (grantType) {
                    OAuthGrantTypes.AuthorizationCode -> {
                        if (state != "state1" && state != null) {
                            throw OAuth2Exception.InvalidGrant("Wrong state $state")
                        }
                        if (code != "code1" && code != "code2") {
                            throw OAuth2Exception.InvalidGrant("Wrong code $code")
                        }
                        if (((code == "code1") && (state == null)) || ((code == "code2") && (state != null))) {
                            throw OAuth2Exception.InvalidGrant("Wrong code $code or state $state")
                        }
                        if (redirectUri != "http://localhost/login") {
                            throw OAuth2Exception.InvalidGrant("Wrong redirect $redirectUri")
                        }
                        if (userName != null || password != null) {
                            throw OAuth2Exception.UnknownException(
                                "User/password shouldn't be specified for authorization_code grant type.",
                                "none"
                            )
                        }

                        return OAuthAccessTokenResponse.OAuth2(
                            "accessToken1",
                            "type",
                            Long.MAX_VALUE,
                            null,
                            when (state) {
                                null -> parametersOf("noState", "Had no state")
                                else -> Parameters.Empty
                            },
                            state
                        )
                    }

                    OAuthGrantTypes.Password -> {
                        if (userName != "user1") {
                            throw OAuth2Exception.InvalidGrant("Wrong username $userName")
                        }
                        if (password != "password1") {
                            throw OAuth2Exception.InvalidGrant("Wrong password $password")
                        }
                        if (state != null || code != null) {
                            throw OAuth2Exception.UnknownException(
                                "State/code shouldn't be specified for password grant type.",
                                "none"
                            )
                        }

                        return OAuthAccessTokenResponse.OAuth2("accessToken1", "type", Long.MAX_VALUE, null)
                    }

                    else -> {
                        throw OAuth2Exception.UnsupportedGrantType(grantType)
                    }
                }
            }
        }
    )

    val failures = ArrayList<Throwable>()
    fun Application.module(settings: OAuthServerSettings.OAuth2ServerSettings = DefaultSettings) {
        install(Authentication) {
            oauth("login") {
                client = testClient
                providerLookup = { settings }
                urlProvider = { "http://localhost/login" }
            }
            basic("resource") {
                realm = "oauth2"
                validate {
                    try {
                        verifyWithOAuth2(it, testClient, settings)
                    } catch (ioe: OAuth2Exception) {
                        null
                    }
                }
            }
        }
        routing {
            authenticate("login") {
                route("/login") {
                    handle {
                        @Suppress("DEPRECATION_ERROR")
                        val principal = call.authentication.principal as? OAuthAccessTokenResponse.OAuth2
                        call.respondText("Hej, $principal")
                    }
                }
            }
            authenticate("resource") {
                get("/resource") {
                    call.respondText("ok")
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        testClient.close()
    }

    @Test
    fun testRedirect() = testApplication {
        application { module() }
        val result = noRedirectsClient().get("/login")

        assertEquals(HttpStatusCode.Found, result.status)

        val url = Url(
            result.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response")
        )
        assertEquals("/authorize", url.encodedPath)
        assertEquals("login-server-com", url.host)

        val query = url.parameters
        assertEquals("clientId1", query[OAuth2RequestParameters.ClientId])
        assertEquals("code", query[OAuth2RequestParameters.ResponseType])
        assertNotNull(query[OAuth2RequestParameters.State])
        assertEquals("http://localhost/login", query[OAuth2RequestParameters.RedirectUri])
    }

    @Test
    fun testRedirectWithScopes() = testApplication {
        application { module(DefaultSettingsWithScopes) }
        val result = noRedirectsClient().get("/login")

        assertEquals(HttpStatusCode.Found, result.status)

        val url = Url(
            result.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response")
        )
        assertEquals("/authorize", url.encodedPath)
        assertEquals("login-server-com", url.host)

        val query = url.parameters
        assertEquals("clientId1", query[OAuth2RequestParameters.ClientId])
        assertEquals("code", query[OAuth2RequestParameters.ResponseType])
        assertNotNull(query[OAuth2RequestParameters.State])
        assertEquals("http://localhost/login", query[OAuth2RequestParameters.RedirectUri])
        assertEquals("http://example.com/scope1 http://example.com/scope2", query[OAuth2RequestParameters.Scope])
    }

    @Test
    fun testRedirectWithExtraParameters() = testApplication {
        application { module(DefaultSettingsWithExtraParameters) }
        val result = noRedirectsClient().get("/login")

        assertEquals(HttpStatusCode.Found, result.status)

        val url = Url(
            result.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response")
        )
        assertEquals("/authorize", url.encodedPath)
        assertEquals("login-server-com", url.host)

        val query = url.parameters
        assertEquals("clientId1", query[OAuth2RequestParameters.ClientId])
        assertEquals("code", query[OAuth2RequestParameters.ResponseType])
        assertNotNull(query[OAuth2RequestParameters.State])
        assertEquals("http://localhost/login", query[OAuth2RequestParameters.RedirectUri])
        assertEquals(listOf("a1", "a2"), query.getAll("a"))
        assertEquals(listOf("b1"), query.getAll("b"))
    }

    @Test
    fun testRedirectCustomizedByInterceptor() = testApplication {
        application { module(DefaultSettingsWithInterceptor) }
        val result = noRedirectsClient().get("/login")

        assertEquals(HttpStatusCode.Found, result.status)

        val url = Url(
            result.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response")
        )
        assertEquals("/authorize", url.encodedPath)
        assertEquals("login-server-com", url.host)

        val query = url.parameters
        assertEquals("clientId1", query[OAuth2RequestParameters.ClientId])
        assertEquals("code", query[OAuth2RequestParameters.ResponseType])
        assertNotNull(query[OAuth2RequestParameters.State])
        assertEquals("http://localhost/login", query[OAuth2RequestParameters.RedirectUri])
        assertEquals("value1", query["custom"])
    }

    @Test
    fun testRequestToken() = testApplication {
        application { module() }
        val result = client.get(
            "/login?" + listOf(
                OAuth2RequestParameters.Code to "code1",
                OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        )

        assertEquals(HttpStatusCode.OK, result.status)
    }

    @Test
    fun testRequestTokenMethodPost() = testApplication {
        application { module(DefaultSettingsWithMethodPost) }
        val result = client.get(
            "/login?" + listOf(
                OAuth2RequestParameters.Code to "code1",
                OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        )

        assertEquals(HttpStatusCode.OK, result.status)
    }

    @Test
    fun testRequestTokenFormPost() = testApplication {
        application { module() }
        val result = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
                ).formUrlEncode()
            )
        }

        assertEquals(HttpStatusCode.OK, result.status)
    }

    @Test
    fun testRequestTokenBadCode() = testApplication {
        application { module() }
        val call = noRedirectsClient().get(
            "/login?" + listOf(
                OAuth2RequestParameters.Code to "code2",
                OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        )

        assertEquals(HttpStatusCode.Found, call.status)
        assertNotNull(call.headers[HttpHeaders.Location])
        assertTrue { call.headers[HttpHeaders.Location]!!.startsWith("https://login-server-com/authorize") }
    }

    @Test
    fun testRequestTokenErrorRedirect() = testApplication {
        application {
            module()
            intercept(ApplicationCallPipeline.Call) {
                assertTrue {
                    call.authentication.allFailures.all {
                        it is OAuth2RedirectError && it.error == "access_denied"
                    }
                } // ktlint-disable max-line-length
            }
        }
        val call = noRedirectsClient().get(
            "/login?" + listOf(
                OAuth2RequestParameters.Error to "access_denied",
                OAuth2RequestParameters.ErrorDescription to "User denied access"
            ).formUrlEncode()
        )

        assertEquals(HttpStatusCode.Found, call.status)
        assertNotNull(call.headers[HttpHeaders.Location])
        assertTrue {
            call.headers[HttpHeaders.Location]!!.startsWith("https://login-server-com/authorize")
        }
    }

    @Test
    fun testResourceOwnerPasswordCredentials() = testApplication {
        application { module() }
        handleRequestWithBasic("/resource", "user", "pass").let { result ->
            assertWWWAuthenticateHeaderExist(result)
        }

        handleRequestWithBasic("/resource", "user1", "password1").let { result ->
            assertFailures()
            assertEquals("ok", result.bodyAsText())
        }
    }

    @Test
    fun testParamsInURL() = testApplication {
        install(Authentication) {
            oauth("login") {
                client = this@testApplication.client
                urlProvider = { "http://localhost/login" }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "oauth2",
                        authorizeUrl = "http://localhost/authorize",
                        accessTokenUrl = "http://localhost/oauth/access_token",
                        clientId = "clientId1",
                        clientSecret = "clientSecret1",
                        requestMethod = HttpMethod.Post,
                        passParamsInURL = true
                    )
                }
            }
        }
        routing {
            post("/oauth/access_token") {
                // If these fail, you will see '401 Unauthorized' in test logs.
                assertEquals(call.request.queryParameters[OAuth2RequestParameters.Code], "mow", "Code is in URL")
                assertEquals(call.request.queryParameters[OAuth2RequestParameters.State], "wow", "State is in URL")
                call.respondText("access_token=a_token", ContentType.Application.FormUrlEncoded)
            }
            authenticate("login") {
                get("/login") {
                    call.respond("We're in.")
                }
            }
        }

        client.get("/login?code=mow&state=wow").also {
            // Usually 401 here means, that tests above failed.
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText(), "We're in.")
        }
    }

    @Test
    fun testExtraTokenParams() = testApplication {
        install(Authentication) {
            oauth("login") {
                client = this@testApplication.client
                urlProvider = { "http://localhost/login" }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "oauth2",
                        authorizeUrl = "http://localhost/authorize",
                        accessTokenUrl = "http://localhost/oauth/access_token",
                        clientId = "clientId1",
                        clientSecret = "clientSecret1",
                        requestMethod = HttpMethod.Post,
                        extraTokenParameters = listOf("a" to "a1", "a" to "a2", "b" to "b1")
                    )
                }
            }
        }
        routing {
            post("/oauth/access_token") {
                val parameters = call.receiveParameters()
                assertEquals(listOf("a1", "a2"), parameters.getAll("a"))
                assertEquals(listOf("b1"), parameters.getAll("b"))
                call.respondText("access_token=a_token", ContentType.Application.FormUrlEncoded)
            }
            authenticate("login") {
                get("/login") {
                    call.respond("We're in.")
                }
            }
        }

        client.get("/login?code=code&state=state").also {
            // Usually 401 here means, that tests above failed.
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText(), "We're in.")
        }
    }

    @Test
    fun testFailedNonce() = testApplication {
        install(Authentication) {
            oauth("login") {
                client = this@testApplication.client
                urlProvider = { "http://localhost/login" }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "oauth2",
                        authorizeUrl = "http://localhost/authorize",
                        accessTokenUrl = "http://localhost/oauth/access_token",
                        clientId = "clientId1",
                        clientSecret = "clientSecret1",
                        requestMethod = HttpMethod.Post,
                        nonceManager = object : NonceManager {
                            override suspend fun newNonce(): String = "some_nonce"
                            override suspend fun verifyNonce(nonce: String): Boolean = false
                        }
                    )
                }
            }
        }
        routing {
            post("/oauth/access_token") {
                call.respondText("access_token=a_token", ContentType.Application.FormUrlEncoded)
            }
            authenticate("login") {
                get("/login") {
                    call.respond("We're in.")
                }
            }
        }

        val authorizeResponse = noRedirectsClient().get("/login")
        val redirectUrl = Url(authorizeResponse.headers[HttpHeaders.Location]!!)
        val state = redirectUrl.parameters["state"]!!
        assertEquals("some_nonce", state)
        val failedNonceResponse = client.get("/login?code=some_code&state=$state")
        assertEquals(HttpStatusCode.Unauthorized, failedNonceResponse.status)
    }

    @Test
    fun testApplicationState() = testApplication {
        @Serializable
        class UserSession(val token: String)

        val client = createClient {
            install(HttpCookies)
        }
        val redirects = mutableMapOf<String, String>()
        externalServices {
            hosts("http://oauth.com") {
                routing {
                    post("/oauth/access_token") {
                        call.respondText("access_token=a_token", ContentType.Application.FormUrlEncoded)
                    }
                    get("/oauth/authorize") {
                        val state = call.parameters["state"]!!
                        call.respondText("code=code&state=$state", ContentType.Application.FormUrlEncoded)
                    }
                }
            }
        }
        install(Sessions) {
            cookie<UserSession>("user_session")
        }
        install(Authentication) {
            oauth("login") {
                this@oauth.client = client
                urlProvider = { "http://localhost/login" }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "oauth2",
                        authorizeUrl = "http://oauth.com/oauth/authorize",
                        accessTokenUrl = "http://oauth.com/oauth/access_token",
                        clientId = "clientId1",
                        clientSecret = "clientSecret1",
                        requestMethod = HttpMethod.Post,
                        onStateCreated = { call, state ->
                            redirects[state] = call.request.queryParameters["redirectUrl"]!!
                        }
                    )
                }
            }
        }
        routing {
            authenticate("login") {
                get("login") {
                    val state = call.principal<OAuthAccessTokenResponse.OAuth2>()!!.state!!
                    call.sessions.set(UserSession(state))
                    val redirect = redirects[state]!!
                    call.respondRedirect(redirect)
                }
            }
            get("{path}") {
                val session = call.sessions.get<UserSession>()
                if (session == null) {
                    val redirectUrl = URLBuilder("http://localhost/login").run {
                        parameters.append("redirectUrl", call.request.uri)
                        build()
                    }
                    call.respondRedirect(redirectUrl)
                    return@get
                }
                call.respond(call.parameters["path"]!!)
            }
        }
        val request1Auth = client.get("/some-url").body<String>().let { parseQueryString(it) }
        val code1 = request1Auth["code"]!!
        val state1 = request1Auth["state"]!!
        val response1 = client.get("/login?code=$code1&state=$state1")
        assertEquals("some-url", response1.bodyAsText())
    }

    private fun assertFailures() {
        failures.forEach {
            throw it
        }
    }
}

private suspend fun ApplicationTestBuilder.handleRequestWithBasic(url: String, user: String, pass: String) =
    client.get(url) {
        val up = "$user:$pass"
        val encoded = up.toByteArray(Charsets.ISO_8859_1).encodeBase64()
        header(HttpHeaders.Authorization, "Basic $encoded")
    }

private fun assertWWWAuthenticateHeaderExist(response: HttpResponse) {
    assertNotNull(response.headers[HttpHeaders.WWWAuthenticate])
    val header =
        parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized

    assertEquals(AuthScheme.Basic, header.authScheme)
    assertEquals("oauth2", header.parameter(HttpAuthHeader.Parameters.Realm))
}

internal interface OAuth2Server {
    fun requestToken(
        clientId: String,
        clientSecret: String,
        grantType: String,
        state: String?,
        code: String?,
        redirectUri: String?,
        userName: String?,
        password: String?
    ): OAuthAccessTokenResponse.OAuth2
}

internal fun createOAuth2Server(server: OAuth2Server): HttpClient {
    val environment = createTestEnvironment {}
    val props = applicationProperties(environment) {
        module {
            routing {
                route("/oauth/access_token") {
                    handle {
                        val formData = runCatching {
                            call.receiveNullable<Parameters>()
                        }.getOrNull() ?: Parameters.Empty

                        val values = call.parameters + formData

                        val clientId = values.requireParameter(OAuth2RequestParameters.ClientId)
                        val clientSecret = values.requireParameter(OAuth2RequestParameters.ClientSecret)
                        val grantType = values.requireParameter(OAuth2RequestParameters.GrantType)
                        val state = values[OAuth2RequestParameters.State]
                        val code = values[OAuth2RequestParameters.Code]
                        val redirectUri = values[OAuth2RequestParameters.RedirectUri]
                        val username = values[OAuth2RequestParameters.UserName]
                        val password = values[OAuth2RequestParameters.Password]
                        val badContentType = values["badContentType"] == "true"
                        val respondStatus = values["respondHttpStatus"]

                        val obj = try {
                            val tokens = server.requestToken(
                                clientId,
                                clientSecret,
                                grantType,
                                state,
                                code,
                                redirectUri,
                                username,
                                password
                            )

                            val jsonMap = buildMap {
                                put(OAuth2ResponseParameters.AccessToken, JsonPrimitive(tokens.accessToken))
                                put(OAuth2ResponseParameters.TokenType, JsonPrimitive(tokens.tokenType))
                                put(OAuth2ResponseParameters.ExpiresIn, JsonPrimitive(tokens.expiresIn))
                                put(OAuth2ResponseParameters.RefreshToken, JsonPrimitive(tokens.refreshToken))
                                for (extraParam in tokens.extraParameters.flattenEntries()) {
                                    put(extraParam.first, JsonPrimitive(extraParam.second))
                                }
                                put("NOT_PRIMITIVE", JsonObject(mapOf("test" to JsonPrimitive("value"))))
                            }
                            JsonObject(jsonMap)
                        } catch (cause: OAuth2Exception) {
                            val jsonMap = buildMap<String, JsonElement> {
                                put(OAuth2ResponseParameters.Error, JsonPrimitive(cause.errorCode ?: "?"))
                                put(OAuth2ResponseParameters.ErrorDescription, JsonPrimitive(cause.message))
                            }
                            JsonObject(jsonMap)
                        } catch (t: Throwable) {
                            val jsonMap = buildMap<String, JsonElement> {
                                // in fact we should provide code here, good enough for testing
                                put(OAuth2ResponseParameters.Error, JsonPrimitive(1))
                                put(OAuth2ResponseParameters.ErrorDescription, JsonPrimitive(t.message))
                            }
                            JsonObject(jsonMap)
                        }

                        val contentType = when {
                            badContentType -> ContentType.Text.Plain
                            else -> ContentType.Application.Json
                        }

                        val status = respondStatus?.let { HttpStatusCode.fromValue(it.toInt()) } ?: HttpStatusCode.OK
                        call.respondText(Json.encodeToString(JsonObject.serializer(), obj), contentType, status)
                    }
                }
            }
        }
    }
    val embeddedServer = EmbeddedServer(props, TestEngine)
    embeddedServer.start()
    return embeddedServer.engine.client.config {
        expectSuccess = false
    }
}

internal fun Parameters.requireParameter(name: String) = get(name)
    ?: throw IllegalArgumentException("No parameter $name specified")
