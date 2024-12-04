/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Represents an HTTP method (verb)
 * @property value contains method name
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.HttpMethod)
 */
public data class HttpMethod(val value: String) {
    @Suppress("KDocMissingDocumentation", "PublicApiImplicitType")
    public companion object {
        public val Get: HttpMethod = HttpMethod("GET")
        public val Post: HttpMethod = HttpMethod("POST")
        public val Put: HttpMethod = HttpMethod("PUT")

        // https://tools.ietf.org/html/rfc5789
        public val Patch: HttpMethod = HttpMethod("PATCH")
        public val Delete: HttpMethod = HttpMethod("DELETE")
        public val Head: HttpMethod = HttpMethod("HEAD")
        public val Options: HttpMethod = HttpMethod("OPTIONS")

        /**
         * Parse HTTP method by [method] string
         */
        public fun parse(method: String): HttpMethod {
            return when (method) {
                Get.value -> Get
                Post.value -> Post
                Put.value -> Put
                Patch.value -> Patch
                Delete.value -> Delete
                Head.value -> Head
                Options.value -> Options
                else -> HttpMethod(method)
            }
        }

        /**
         * A list of default HTTP methods
         */
        public val DefaultMethods: List<HttpMethod> = listOf(Get, Post, Put, Patch, Delete, Head, Options)
    }
}
