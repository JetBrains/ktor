/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * A configuration for the [Authentication] plugin.
 */
@KtorDsl
public class AuthenticationConfig(providers: Map<String?, AuthenticationProvider> = emptyMap()) {
    internal val providers = providers.toMutableMap()

    /**
     * Registers a provider with the specified [name] and allows you to [configure] it.
     * @throws IllegalArgumentException if a provider with the same name is already installed.
     */
    public fun provider(name: String? = null, configure: DynamicProviderConfig.() -> Unit) {
        requireProviderNotRegistered(name)
        val configuration = DynamicProviderConfig(name).apply(configure)
        val provider = configuration.buildProvider()
        providers[provider.name] = provider
    }

    /**
     * Registers the specified [provider].
     * @throws IllegalArgumentException if a provider with the same name is already installed.
     */
    public fun register(provider: AuthenticationProvider) {
        requireProviderNotRegistered(provider.name)
        providers[provider.name] = provider
    }

    private fun requireProviderNotRegistered(providerName: String?) {
        if (providers.containsKey(providerName)) {
            throw IllegalArgumentException("Provider with the name $providerName is already registered")
        }
    }

    internal fun copy(): AuthenticationConfig = AuthenticationConfig(providers)
}

/**
 * A plugin that handles authentication and authorization.
 * Typical usage scenarios include logging in users, granting access to specific resources,
 * and securely transmitting information between parties.
 *
 * Ktor supports multiple authentications and authorization schemes, including the `Basic` and `Digest`
 * HTTP authentication schemes, JSON Web Tokens, OAuth, and so on.
 *
 * A configuration of the `Authentication` plugin might look as follows:
 * 1. Choose and configure an authentication provider.
 *    The code snippet below shows how to create the `basic` provider:
 *    ```kotlin
 *    install(Authentication) {
 *        basic("auth-basic") {
 *            // Configure basic authentication
 *        }
 *    }
 *    ```
 *
 * 2. Protect a desired resource using the [io.ktor.server.routing.Route.authenticate] function
 *    that accepts a name of the authentication provider:
 *    ```kotlin
 *    routing {
 *        authenticate("auth-basic") {
 *            get("/orders") {
 *                // ...
 *            }
 *        }
 *    }
 *    ```
 *
 * You can learn how to configure various authentication providers from
 * [Authentication and authorization](https://ktor.io/docs/authentication.html).
 */
public class Authentication(internal var config: AuthenticationConfig) {

    /**
     * Configures an already installed plugin.
     */
    public fun configure(block: AuthenticationConfig.() -> Unit) {
        val newConfiguration = config.copy()
        block(newConfiguration)
        config = newConfiguration.copy()
    }

    /**
     * An installation object of the [Authentication] plugin.
     */
    public companion object : BaseApplicationPlugin<Application, AuthenticationConfig, Authentication> {
        override val key: AttributeKey<Authentication> = AttributeKey("AuthenticationHolder")

        override fun install(pipeline: Application, configure: AuthenticationConfig.() -> Unit): Authentication {
            val config = AuthenticationConfig().apply(configure)
            return Authentication(config)
        }
    }
}

/**
 * Retrieves an [AuthenticationContext] for `this` call.
 */
public val ApplicationCall.authentication: AuthenticationContext
    get() = AuthenticationContext.from(this)

/**
 * Retrieves an authenticated principal [Any] for `this` call.
 */
public inline fun <reified P : Any> ApplicationCall.principal(): P? = principal(null)

/**
 * Retrieves an authenticated principal [Any] for `this` call from provider with name [provider]
 */
public inline fun <reified P : Any> ApplicationCall.principal(provider: String?): P? =
    authentication.principal(provider)

/**
 * Installs the [Authentication] plugin if not yet installed and invokes [block] on its config.
 * You can modify the existing authentication configuration only in the [authentication]'s block or
 * using the [Authentication.configure] function.
 * Changing captured instance of configuration outside of [block] may have no effect or damage application's state.
 */
public fun Application.authentication(block: AuthenticationConfig.() -> Unit) {
    pluginOrNull(Authentication)?.configure(block) ?: install(Authentication, block)
}
