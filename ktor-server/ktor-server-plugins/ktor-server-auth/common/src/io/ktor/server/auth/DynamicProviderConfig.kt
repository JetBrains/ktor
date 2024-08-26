/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

/**
 * A configuration that creates a provider based on the [AuthenticationConfig.provider] block.
 */
public class DynamicProviderConfig(name: String?) : AuthenticationProvider.Config(name) {

    private lateinit var authenticateFunction: (context: AuthenticationContext) -> Unit

    public fun authenticate(block: (context: AuthenticationContext) -> Unit) {
        authenticateFunction = block
    }

    internal fun buildProvider(): AuthenticationProvider {
        check(::authenticateFunction.isInitialized) {
            "Please configure authentication by calling authenticate() function"
        }
        return object : AuthenticationProvider(this) {
            override suspend fun onAuthenticate(context: AuthenticationContext) {
                authenticateFunction(context)
            }
        }
    }
}
