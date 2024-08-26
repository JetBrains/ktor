/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors

import io.ktor.http.*
import io.ktor.http.HttpMethod.Companion.DefaultMethods
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * A configuration for the [io.ktor.server.plugins.cors.routing.CORS] plugin.
 */
@KtorDsl
public class CORSConfig {
    private val wildcardWithDot = "*."

    public companion object {

        /**
         * The default CORS max age value.
         */
        public const val CORS_DEFAULT_MAX_AGE: Long = 24L * 3600 // 1 day

        /**
         * Default HTTP methods that are always allowed by CORS.
         */
        public val CorsDefaultMethods: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Head)

        /**
         * Default HTTP headers that are always allowed by CORS
         * (simple request headers according to https://www.w3.org/TR/cors/#simple-header).
         * Note that `Content-Type` header simplicity depends on its value.
         */
        public val CorsSimpleRequestHeaders: Set<String> = caseInsensitiveSet(
            HttpHeaders.Accept,
            HttpHeaders.AcceptLanguage,
            HttpHeaders.ContentLanguage,
            HttpHeaders.ContentType
        )

        /**
         * Default HTTP headers that are always allowed by CORS to be used in a response
         * (simple request headers according to https://www.w3.org/TR/cors/#simple-header).
         */
        public val CorsSimpleResponseHeaders: Set<String> = caseInsensitiveSet(
            HttpHeaders.CacheControl,
            HttpHeaders.ContentLanguage,
            HttpHeaders.ContentType,
            HttpHeaders.Expires,
            HttpHeaders.LastModified,
            HttpHeaders.Pragma
        )

        /**
         * The allowed set of content types that are allowed by CORS without preflight check.
         */
        @Suppress("unused")
        public val CorsSimpleContentTypes: Set<ContentType> =
            setOf(
                ContentType.Application.FormUrlEncoded,
                ContentType.MultiPart.FormData,
                ContentType.Text.Plain
            ).unmodifiable()

        @OptIn(InternalAPI::class)
        private fun caseInsensitiveSet(vararg elements: String): Set<String> =
            CaseInsensitiveSet(elements.asList())
    }

    /**
     * Allowed [CORS] hosts.
     */
    public val hosts: MutableSet<String> = HashSet()

    /**
     * Allowed [CORS] headers.
     */
    @OptIn(InternalAPI::class)
    public val headers: MutableSet<String> = CaseInsensitiveSet()

    /**
     * Allowed [CORS] HTTP methods.
     */
    public val methods: MutableSet<HttpMethod> = HashSet()

    /**
     * Exposed HTTP headers that could be accessed by a client.
     */
    @OptIn(InternalAPI::class)
    public val exposedHeaders: MutableSet<String> = CaseInsensitiveSet()

    /**
     * Allows passing credential information (such as cookies or authentication information)
     * with cross-origin requests.
     * This property sets the `Access-Control-Allow-Credentials` response header to `true`.
     */
    public var allowCredentials: Boolean = false

    /**
     * If present allows any origin matching any of the predicates.
     */
    internal val originPredicates: MutableList<(String) -> Boolean> = mutableListOf()

    /**
     * If present represents the prefix for headers which are permitted in CORS requests.
     */
    public val headerPredicates: MutableList<(String) -> Boolean> = mutableListOf()

    /**
     * Specifies how long the response to the preflight request can be cached
     * without sending another preflight request.
     */
    public var maxAgeInSeconds: Long = CORS_DEFAULT_MAX_AGE
        set(newMaxAge) {
            check(newMaxAge >= 0L) { "maxAgeInSeconds shouldn't be negative: $newMaxAge" }
            field = newMaxAge
        }

    /**
     * Allows requests from the same origin.
     */
    public var allowSameOrigin: Boolean = true

    /**
     * Allows sending requests with non-simple content-types. The following content types are considered simple:
     * - `text/plain`
     * - `application/x-www-form-urlencoded`
     * - `multipart/form-data`
     */
    public var allowNonSimpleContentTypes: Boolean = false

    /**
     * Allows requests from any host.
     */
    public fun anyHost() {
        hosts.add("*")
    }

    /**
     * Allows requests from the specified domains and schemes.
     * A wildcard is supported for either the host or any subdomain.
     * If you specify a wildcard in the host, you cannot add specific subdomains.
     * Otherwise, you can mix wildcard and non-wildcard subdomains as long as
     * the wildcard is always in front of the domain, e.g. `*.sub.domain.com` but not `sub.*.domain.com`.
     *
     * @param host host as it appears in the Host header (e.g. localhost:8080)
     * @param schemes protocols allowed for the origin site; defaults to http and https
     * @param subDomains additional subdomains for the given host
     */
    public fun allowHost(
        host: String,
        schemes: List<String> = listOf("http", "https"),
        subDomains: List<String> = emptyList()
    ) {
        if (host == "*") return anyHost()

        require("://" !in host) { "scheme should be specified as a separate parameter schemes" }

        for (schema in schemes) {
            addHost("$schema://$host")

            for (subDomain in subDomains) {
                validateWildcardRequirements(subDomain)
                addHost("$schema://$subDomain.$host")
            }
        }
    }

    private fun addHost(host: String) {
        validateWildcardRequirements(host)
        hosts.add(host)
    }

    private fun validateWildcardRequirements(host: String) {
        if ('*' !in host) return

        fun String.countMatches(subString: String): Int =
            windowed(subString.length) { if (it == subString) 1 else 0 }.sum()

        require(wildcardInFrontOfDomain(host)) { "wildcard must appear in front of the domain, e.g. *.domain.com" }
        require(host.countMatches(wildcardWithDot) == 1) { "wildcard cannot appear more than once" }
    }

    private fun wildcardInFrontOfDomain(host: String): Boolean {
        val indexOfWildcard = host.indexOf(wildcardWithDot)
        return wildcardWithDot in host && !host.endsWith(wildcardWithDot) &&
            (indexOfWildcard <= 0 || host.substringBefore(wildcardWithDot).endsWith("://"))
    }

    /**
     * Allows exposing the [header] using `Access-Control-Expose-Headers`.
     * The `Access-Control-Expose-Headers` header adds the specified headers
     * to the allowlist that JavaScript in browsers can access.
     */
    public fun exposeHeader(header: String) {
        if (header !in CorsSimpleResponseHeaders) {
            exposedHeaders.add(header)
        }
    }

    /**
     * Allows using the `X-Http-Method-Override` header for the actual [CORS] request.
     */
    @Suppress("unused")
    public fun allowXHttpMethodOverride() {
        allowHeader(HttpHeaders.XHttpMethodOverride)
    }

    /**
     * Allows using an origin matching [predicate] for the actual [CORS] request.
     */
    public fun allowOrigins(predicate: (String) -> Boolean) {
        this.originPredicates.add(predicate)
    }

    /**
     * Allows using headers prefixed with [headerPrefix] for the actual [CORS] request.
     */
    public fun allowHeadersPrefixed(headerPrefix: String) {
        val prefix = headerPrefix.lowercase()
        this.headerPredicates.add { name -> name.startsWith(prefix) }
    }

    /**
     * Allows using headers matching [predicate] for the actual [CORS] request.
     */
    public fun allowHeaders(predicate: (String) -> Boolean) {
        this.headerPredicates.add(predicate)
    }

    /**
     * Allow using a specified [header] for the actual [CORS] request.
     */
    public fun allowHeader(header: String) {
        if (header.equals(HttpHeaders.ContentType, ignoreCase = true)) {
            allowNonSimpleContentTypes = true
            return
        }

        if (header !in CorsSimpleRequestHeaders) {
            headers.add(header)
        }
    }

    /**
     * Adds a specified [method] to a list of methods allowed by [CORS].
     *
     * Note that CORS operates with real HTTP methods only and
     * doesn't handle method overridden by `X-Http-Method-Override`.
     */
    public fun allowMethod(method: HttpMethod) {
        if (method !in CorsDefaultMethods) {
            methods.add(method)
        }
    }

    /**
     * Allows requests with any HTTP method.
     */
    public fun anyMethod() {
        methods.addAll(DefaultMethods)
    }
}
