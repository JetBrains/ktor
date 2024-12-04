/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.darwin.certificates

import io.ktor.client.engine.darwin.certificates.CertificatePinner.*
import io.ktor.client.engine.darwin.certificates.CertificatesInfo.HASH_ALGORITHM_SHA_1
import io.ktor.client.engine.darwin.certificates.CertificatesInfo.HASH_ALGORITHM_SHA_256

/**
 * Represents a pinned certificate. Recommended using [Builder.add] to construct
 * [CertificatePinner]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.darwin.certificates.PinnedCertificate)
 */
public data class PinnedCertificate(
    /**
     * A hostname like `example.com` or a pattern like `*.example.com` (canonical form).
     */
    private val pattern: String,
    /**
     * Either `sha1/` or `sha256/`.
     */
    val hashAlgorithm: String,
    /**
     * The hash of the pinned certificate using [hashAlgorithm].
     */
    val hash: String
) {
    /**
     * Checks whether the given [hostname] matches the [pattern] of this [PinnedCertificate]
     * @param hostname The hostname to check
     * @return Boolean TRUE if it matches
     */
    internal fun matches(hostname: String): Boolean = when {
        pattern.startsWith("**.") -> {
            // With ** empty prefixes match so exclude the dot from regionMatches().
            val suffixLength = pattern.length - 3
            val prefixLength = hostname.length - suffixLength
            hostname.regionMatches(
                thisOffset = hostname.length - suffixLength,
                other = pattern,
                otherOffset = 3,
                length = suffixLength
            ) && (prefixLength == 0 || hostname[prefixLength - 1] == '.')
        }
        pattern.startsWith("*.") -> {
            // With * there must be a prefix so include the dot in regionMatches().
            val suffixLength = pattern.length - 1
            val prefixLength = hostname.length - suffixLength
            hostname.regionMatches(
                thisOffset = hostname.length - suffixLength,
                other = pattern,
                otherOffset = 1,
                length = suffixLength
            ) && hostname.lastIndexOf('.', prefixLength - 1) == -1
        }
        else -> hostname == pattern
    }

    override fun toString(): String = hashAlgorithm + hash

    public companion object {
        /**
         * Create a new Pin
         * @param pattern The hostname pattern
         * @param pin The hash to pin
         * @return [PinnedCertificate] The new pin
         */
        public fun new(pattern: String, pin: String): PinnedCertificate {
            require(
                pattern.startsWith("*.") && pattern.indexOf("*", 1) == -1 ||
                    pattern.startsWith("**.") && pattern.indexOf("*", 2) == -1 ||
                    pattern.indexOf("*") == -1
            ) {
                "Unexpected pattern: $pattern"
            }
            val canonicalPattern = pattern.lowercase()
            return when {
                pin.startsWith(HASH_ALGORITHM_SHA_1) -> {
                    val hash = pin.substring(HASH_ALGORITHM_SHA_1.length)
                    PinnedCertificate(
                        pattern = canonicalPattern,
                        hashAlgorithm = HASH_ALGORITHM_SHA_1,
                        hash = hash
                    )
                }
                pin.startsWith(HASH_ALGORITHM_SHA_256) -> {
                    val hash = pin.substring(HASH_ALGORITHM_SHA_256.length)
                    PinnedCertificate(
                        pattern = canonicalPattern,
                        hashAlgorithm = HASH_ALGORITHM_SHA_256,
                        hash = hash
                    )
                }
                else -> throw IllegalArgumentException(
                    "Pins must start with '$HASH_ALGORITHM_SHA_256' or '$HASH_ALGORITHM_SHA_1': $pin"
                )
            }
        }
    }
}
