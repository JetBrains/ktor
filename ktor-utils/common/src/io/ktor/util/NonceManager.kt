/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.utils.io.*

/**
 * Represents a nonce manager. Its responsibility is to produce nonce values
 * and verify nonce values from untrusted sources that they are provided by this manager.
 * This is usually required in web environment to mitigate CSRF attacks.
 * Depending on it's underlying implementation it could be stateful or stateless.
 * Note that there is usually some timeout for nonce values to reduce memory usage and to avoid replay attacks.
 * Nonce length is unspecified.
 */
public interface NonceManager {
    /**
     * Generate new nonce instance
     */
    public suspend fun newNonce(): String

    /**
     * Verify [nonce] value
     * @return `true` if [nonce] is valid
     */
    public suspend fun verifyNonce(nonce: String): Boolean
}

/**
 * This implementation does only generate nonce values but doesn't validate them. This is recommended for testing only.
 */
public object GenerateOnlyNonceManager : NonceManager {
    override suspend fun newNonce(): String {
        return generateNonce()
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        return true
    }
}
