/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*

internal val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.auth.Auth")

private class AtomicCounter {
    val atomic = atomic(0)
}

@KtorDsl
public class AuthConfig {
    public val providers: MutableList<AuthProvider> = mutableListOf()
}

/**
 * Shows that request should skip auth and refresh token procedure.
 */
public val AuthCircuitBreaker: AttributeKey<Unit> = AttributeKey("auth-request")

/**
 * A client's plugin that handles authentication and authorization.
 * Typical usage scenarios include logging in users and gaining access to specific resources.
 *
 * You can learn more from [Authentication and authorization](https://ktor.io/docs/auth.html).
 *
 * [providers] - list of auth providers to use.
 */
public val Auth: ClientPlugin<AuthConfig> = createClientPlugin("Auth", ::AuthConfig) {
    val providers = pluginConfig.providers.toList()

    client.attributes.put(AuthProvidersKey, providers)

    val tokenVersions = ConcurrentMap<AuthProvider, AtomicCounter>()
    val tokenVersionsAttributeKey =
        AttributeKey<MutableMap<AuthProvider, Int>>("ProviderVersionAttributeKey")

    @OptIn(InternalAPI::class)
    fun findProvider(
        call: HttpClientCall,
        candidateProviders: Set<AuthProvider>
    ): Pair<AuthProvider, HttpAuthHeader?>? {
        val headerValues = call.response.headers.getAll(HttpHeaders.WWWAuthenticate)
        val authHeaders = headerValues?.map { parseAuthorizationHeaders(it) }?.flatten() ?: emptyList()

        return when {
            authHeaders.isEmpty() && candidateProviders.size == 1 -> {
                candidateProviders.first() to null
            }

            authHeaders.isEmpty() -> {
                LOGGER.trace(
                    "401 response ${call.request.url} has no or empty \"WWW-Authenticate\" header. " +
                        "Can not add or refresh token"
                )
                null
            }

            else -> authHeaders.firstNotNullOfOrNull { header ->
                candidateProviders.find { it.isApplicable(header) }?.let { it to header }
            }
        }
    }

    suspend fun refreshTokenIfNeeded(
        call: HttpClientCall,
        provider: AuthProvider,
        request: HttpRequestBuilder
    ): Boolean {
        val tokenVersion = tokenVersions.computeIfAbsent(provider) { AtomicCounter() }
        val requestTokenVersions = request.attributes
            .computeIfAbsent(tokenVersionsAttributeKey) { mutableMapOf() }
        val requestTokenVersion = requestTokenVersions[provider]

        if (requestTokenVersion != null && requestTokenVersion >= tokenVersion.atomic.value) {
            LOGGER.trace("Refreshing token for ${call.request.url}")
            if (!provider.refreshToken(call.response)) {
                LOGGER.trace("Refreshing token failed for ${call.request.url}")
                return false
            } else {
                requestTokenVersions[provider] = tokenVersion.atomic.incrementAndGet()
            }
        }
        return true
    }

    @OptIn(InternalAPI::class)
    suspend fun Send.Sender.executeWithNewToken(
        call: HttpClientCall,
        provider: AuthProvider,
        oldRequest: HttpRequestBuilder,
        authHeader: HttpAuthHeader?
    ): HttpClientCall {
        val request = HttpRequestBuilder()
        request.takeFromWithExecutionContext(oldRequest)
        provider.addRequestHeaders(request, authHeader)
        request.attributes.put(AuthCircuitBreaker, Unit)

        LOGGER.trace("Sending new request to ${call.request.url}")
        return proceed(request)
    }

    onRequest { request, _ ->
        providers.filter { it.sendWithoutRequest(request) }.forEach { provider ->
            LOGGER.trace("Adding auth headers for ${request.url} from provider $provider")
            val tokenVersion = tokenVersions.computeIfAbsent(provider) { AtomicCounter() }
            val requestTokenVersions = request.attributes
                .computeIfAbsent(tokenVersionsAttributeKey) { mutableMapOf() }
            requestTokenVersions[provider] = tokenVersion.atomic.value
            provider.addRequestHeaders(request)
        }
    }

    on(Send) { originalRequest ->
        val origin = proceed(originalRequest)
        if (origin.response.status != HttpStatusCode.Unauthorized) return@on origin
        if (origin.request.attributes.contains(AuthCircuitBreaker)) return@on origin

        var call = origin

        val candidateProviders = HashSet(providers)

        while (call.response.status == HttpStatusCode.Unauthorized) {
            LOGGER.trace("Received 401 for ${call.request.url}")

            val (provider, authHeader) = findProvider(call, candidateProviders) ?: run {
                LOGGER.trace("Can not find auth provider for ${call.request.url}")
                return@on call
            }

            LOGGER.trace("Using provider $provider for ${call.request.url}")

            candidateProviders.remove(provider)
            if (!refreshTokenIfNeeded(call, provider, originalRequest)) return@on call
            call = executeWithNewToken(call, provider, originalRequest, authHeader)
        }
        return@on call
    }
}

/**
 * Install [Auth] plugin.
 */
public fun HttpClientConfig<*>.Auth(block: AuthConfig.() -> Unit) {
    install(Auth, block)
}

@PublishedApi
internal val AuthProvidersKey: AttributeKey<List<AuthProvider>> = AttributeKey("AuthProviders")

public val HttpClient.authProviders: List<AuthProvider>
    get() = attributes.getOrNull(AuthProvidersKey) ?: emptyList()

public inline fun <reified T : AuthProvider> HttpClient.authProvider(): T? =
    authProviders.filterIsInstance<T>().singleOrNull()
