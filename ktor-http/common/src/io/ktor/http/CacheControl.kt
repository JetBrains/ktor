/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Represents a value for a `Cache-Control` header
 *
 * @param visibility specifies an optional visibility such as private or public
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CacheControl)
 */
public abstract class CacheControl(public val visibility: Visibility?) {

    /**
     * Controls caching by proxies
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CacheControl.Visibility)
     */
    public enum class Visibility(internal val headerValue: String) {
        /**
         * Specifies that the response is cacheable by clients and shared (proxy) caches.
         *
         * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CacheControl.Visibility.Public)
         */
        Public("public"),

        /**
         * Specifies that the response is cacheable only on the client and not by shared (proxy server) caches.
         *
         * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CacheControl.Visibility.Private)
         */
        Private("private")
    }

    /**
     * Represents a no-cache cache control value
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CacheControl.NoCache)
     */
    public class NoCache(visibility: Visibility?) : CacheControl(visibility) {
        override fun toString(): String = if (visibility == null) {
            "no-cache"
        } else {
            "no-cache, ${visibility.headerValue}"
        }

        override fun equals(other: Any?): Boolean {
            return other is NoCache && visibility == other.visibility
        }

        override fun hashCode(): Int {
            return visibility.hashCode()
        }
    }

    /**
     * Represents a no-store cache control value
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CacheControl.NoStore)
     */
    public class NoStore(visibility: Visibility?) : CacheControl(visibility) {
        override fun toString(): String = if (visibility == null) {
            "no-store"
        } else {
            "no-store, ${visibility.headerValue}"
        }

        override fun equals(other: Any?): Boolean {
            return other is NoStore && other.visibility == visibility
        }

        override fun hashCode(): Int {
            return visibility.hashCode()
        }
    }

    /**
     * Represents a cache control value with the specified max ages and re-validation strategies
     * @property maxAgeSeconds max-age in seconds
     * @property proxyMaxAgeSeconds max-age in seconds for caching proxies
     * @property mustRevalidate `true` if a client must validate in spite of age
     * @property proxyRevalidate `true` if a caching proxy must revalidate in spite of age
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.CacheControl.MaxAge)
     */
    public class MaxAge(
        public val maxAgeSeconds: Int,
        public val proxyMaxAgeSeconds: Int? = null,
        public val mustRevalidate: Boolean = false,
        public val proxyRevalidate: Boolean = false,
        visibility: Visibility? = null
    ) : CacheControl(visibility) {
        override fun toString(): String {
            val parts = ArrayList<String>(5)
            parts.add("max-age=$maxAgeSeconds")
            if (proxyMaxAgeSeconds != null) {
                parts.add("s-maxage=$proxyMaxAgeSeconds")
            }
            if (mustRevalidate) {
                parts.add("must-revalidate")
            }
            if (proxyRevalidate) {
                parts.add("proxy-revalidate")
            }
            if (visibility != null) {
                parts.add(visibility.headerValue)
            }

            return parts.joinToString(", ")
        }

        override fun equals(other: Any?): Boolean {
            return other === this || (
                other is MaxAge &&
                    other.maxAgeSeconds == maxAgeSeconds &&
                    other.proxyMaxAgeSeconds == proxyMaxAgeSeconds &&
                    other.mustRevalidate == mustRevalidate &&
                    other.proxyRevalidate == proxyRevalidate &&
                    other.visibility == visibility
                )
        }

        override fun hashCode(): Int {
            var result = maxAgeSeconds
            result = 31 * result + (proxyMaxAgeSeconds ?: 0)
            result = 31 * result + mustRevalidate.hashCode()
            result = 31 * result + proxyRevalidate.hashCode()
            result = 31 * result + visibility.hashCode()
            return result
        }
    }
}
