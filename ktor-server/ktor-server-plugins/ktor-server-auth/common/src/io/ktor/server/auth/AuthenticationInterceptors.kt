/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.auth.Authentication")

internal object AuthenticationHook : Hook<suspend (ApplicationCall) -> Unit> {
    internal val AuthenticatePhase: PipelinePhase = PipelinePhase("Authenticate")

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, AuthenticatePhase)
        pipeline.intercept(AuthenticatePhase) { handler(call) }
    }
}

/**
 * A hook that is executed after authentication was checked.
 * Note that this hook is also executed for optional authentication or for routes without any authentication,
 * resulting in [ApplicationCall.principal] being `null`.
 */
public object AuthenticationChecked : Hook<suspend (ApplicationCall) -> Unit> {
    internal val AfterAuthenticationPhase: PipelinePhase = PipelinePhase("AfterAuthentication")

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall) -> Unit
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, AuthenticationHook.AuthenticatePhase)
        pipeline.insertPhaseAfter(AuthenticationHook.AuthenticatePhase, AfterAuthenticationPhase)
        pipeline.intercept(AfterAuthenticationPhase) { handler(call) }
    }
}

/**
 * A plugin that authenticates calls. Usually used via the [authenticate] function inside routing.
 */
public val AuthenticationInterceptors: RouteScopedPlugin<RouteAuthenticationConfig> = createRouteScopedPlugin(
    "AuthenticationInterceptors",
    ::RouteAuthenticationConfig
) {
    val providers = pluginConfig.providers
    val authConfig = application.plugin(Authentication).config

    val requiredProviders = authConfig
        .findProviders(providers) { it == AuthenticationStrategy.Required }
    val notRequiredProviders = authConfig
        .findProviders(providers) { it != AuthenticationStrategy.Required } - requiredProviders
    val firstSuccessfulProviders = authConfig
        .findProviders(providers) { it == AuthenticationStrategy.FirstSuccessful } - requiredProviders
    val optionalProviders = authConfig
        .findProviders(providers) { it == AuthenticationStrategy.Optional } -
        requiredProviders - firstSuccessfulProviders

    on(AuthenticationHook) { call ->
        if (call.isHandled) return@on

        val authenticationContext = AuthenticationContext.from(call)
        if (authenticationContext.principal<Any>() != null) return@on

        var count = 0
        for (provider in requiredProviders) {
            if (provider.skipWhen.any { skipCondition -> skipCondition(call) }) {
                LOGGER.trace("Skipping authentication provider ${provider.name} for ${call.request.uri}")
                continue
            }

            LOGGER.trace("Trying to authenticate ${call.request.uri} with required ${provider.name}")
            provider.onAuthenticate(authenticationContext)
            count++
            if (authenticationContext._principal.principals.size < count) {
                LOGGER.trace("Authentication failed for ${call.request.uri} with provider $provider")
                authenticationContext.executeChallenges(call)
                return@on
            }
            LOGGER.trace("Authentication succeeded for ${call.request.uri} with provider $provider")
        }

        for (provider in notRequiredProviders) {
            if (authenticationContext._principal.principals.isNotEmpty()) {
                LOGGER.trace("Authenticate for ${call.request.uri} succeed. Skipping other providers")
                break
            }
            if (provider.skipWhen.any { skipCondition -> skipCondition(call) }) {
                LOGGER.trace("Skipping authentication provider ${provider.name} for ${call.request.uri}")
                continue
            }

            LOGGER.trace("Trying to authenticate ${call.request.uri} with ${provider.name}")
            provider.onAuthenticate(authenticationContext)

            if (authenticationContext._principal.principals.isNotEmpty()) {
                LOGGER.trace("Authentication succeeded for ${call.request.uri} with provider $provider")
            } else {
                LOGGER.trace("Authentication failed for ${call.request.uri} with provider $provider")
            }
        }

        if (authenticationContext._principal.principals.isNotEmpty()) return@on
        val isOptional = optionalProviders.isNotEmpty() &&
            firstSuccessfulProviders.isEmpty() &&
            requiredProviders.isEmpty()
        val isNoInvalidCredentials = authenticationContext.allFailures
            .none { it == AuthenticationFailedCause.InvalidCredentials }
        if (isOptional && isNoInvalidCredentials) {
            LOGGER.trace("Authentication is optional and no credentials were provided for ${call.request.uri}")
            return@on
        }

        authenticationContext.executeChallenges(call)
    }
}

private suspend fun AuthenticationContext.executeChallenges(call: ApplicationCall) {
    val challenges = challenge.challenges

    if (this.executeChallenges(challenges, call)) return

    if (this.executeChallenges(challenge.errorChallenges, call)) return

    for (error in allErrors) {
        if (!challenge.completed) {
            LOGGER.trace("Authentication failed for ${call.request.uri} with error ${error.message}")
            if (!call.isHandled) {
                call.respond(UnauthorizedResponse())
            }
            challenge.complete()
            return
        }
    }
}

private suspend fun AuthenticationContext.executeChallenges(
    challenges: List<ChallengeFunction>,
    call: ApplicationCall
): Boolean {
    for (challengeFunction in challenges) {
        challengeFunction(challenge, call)
        if (challenge.completed) {
            if (!call.isHandled) {
                LOGGER.trace("Responding unauthorized because call is not handled.")
                call.respond(UnauthorizedResponse())
            }
            return true
        }
    }
    return false
}

private fun AuthenticationConfig.findProviders(
    configurations: Collection<AuthenticateProvidersRegistration>,
    filter: (AuthenticationStrategy) -> Boolean
): Set<AuthenticationProvider> {
    return configurations.filter { filter(it.strategy) }
        .flatMap { it.names.map { configurationName -> this.findProvider(configurationName) } }
        .toSet()
}

private fun AuthenticationConfig.findProvider(configurationName: String?): AuthenticationProvider {
    return providers[configurationName] ?: throw IllegalArgumentException(
        if (configurationName == null) {
            "Default authentication configuration was not found. "
        } else {
            "Authentication configuration with the name $configurationName was not found. "
        } + "Make sure that you install Authentication plugin before you use it in Routing"
    )
}

/**
 *  A resolution strategy for nested authentication providers.
 *  [AuthenticationStrategy.Optional] - if no authentication is provided by the client,
 *  a call continues but with a null [Principal].
 *  [AuthenticationStrategy.FirstSuccessful] - client must provide authentication data for at least one provider
 *  registered for this route
 *  [AuthenticationStrategy.Required] - client must provide authentication data for all providers registered for
 *  this route with this strategy
 */
public enum class AuthenticationStrategy { Optional, FirstSuccessful, Required }

/**
 * Creates a route that allows you to define authorization scope for application resources.
 * This function accepts names of authentication providers defined in the [Authentication] plugin configuration.
 * @see [Authentication]
 *
 * @param configurations names of authentication providers defined in the [Authentication] plugin configuration.
 * @param optional when set, if no authentication is provided by the client,
 * a call continues but with a null [Principal].
 * @throws MissingApplicationPluginException if no [Authentication] plugin installed first.
 * @throws IllegalArgumentException if there are no registered providers referred by [configurations] names.
 */
public fun Route.authenticate(
    vararg configurations: String? = arrayOf<String?>(null),
    optional: Boolean = false,
    build: Route.() -> Unit
): Route {
    return authenticate(
        configurations = configurations,
        strategy = if (optional) AuthenticationStrategy.Optional else AuthenticationStrategy.FirstSuccessful,
        build = build
    )
}

/**
 * Creates a route that allows you to define authorization scope for application resources.
 * This function accepts names of authentication providers defined in the [Authentication] plugin configuration.
 * @see [Authentication]
 *
 * @param configurations names of authentication providers defined in the [Authentication] plugin configuration.
 * @param strategy defines resolution strategy for nested authentication providers.
 *  [AuthenticationStrategy.Optional] - if no authentication is provided by the client,
 *  a call continues but with a null [Principal].
 *  [AuthenticationStrategy.FirstSuccessful] - client must provide authentication data for at least one provider
 *  registered for this route
 *  [AuthenticationStrategy.Required] - client must provide authentication data for all providers registered for
 *  this route with this strategy
 * @throws MissingApplicationPluginException if no [Authentication] plugin installed first.
 * @throws IllegalArgumentException if there are no registered providers referred by [configurations] names.
 */
public fun Route.authenticate(
    vararg configurations: String? = arrayOf<String?>(null),
    strategy: AuthenticationStrategy,
    build: Route.() -> Unit
): Route {
    require(configurations.isNotEmpty()) { "At least one configuration name or null for default need to be provided" }

    val configurationNames = configurations.distinct().toList()
    val authenticatedRoute = createChild(AuthenticationRouteSelector(configurationNames))
    authenticatedRoute.attributes.put(
        AuthenticateProvidersKey,
        AuthenticateProvidersRegistration(configurationNames, strategy)
    )
    val allConfigurations = generateSequence(authenticatedRoute) { it.parent }
        .mapNotNull { it.attributes.getOrNull(AuthenticateProvidersKey) }
        .toList()
        .reversed()

    authenticatedRoute.install(AuthenticationInterceptors) {
        this.providers = allConfigurations
    }
    authenticatedRoute.build()
    return authenticatedRoute
}

/**
 * A configuration for the [AuthenticationInterceptors] plugin.
 */
@KtorDsl
public class RouteAuthenticationConfig {
    internal var providers: List<AuthenticateProvidersRegistration> =
        listOf(AuthenticateProvidersRegistration(listOf(null), AuthenticationStrategy.FirstSuccessful))
}

/**
 * An authentication route node that is used by [Authentication] plugin
 * and usually created by the [Route.authenticate] DSL function,
 * so generally there is no need to instantiate it directly unless you are writing an extension.
 * @param names of authentication providers to be applied to this route.
 */
public class AuthenticationRouteSelector(public val names: List<String?>) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }

    override fun toString(): String = "(authenticate ${names.joinToString { it ?: "\"default\"" }})"
}

internal class AuthenticateProvidersRegistration(
    val names: List<String?>,
    val strategy: AuthenticationStrategy
)

private val AuthenticateProvidersKey = AttributeKey<AuthenticateProvidersRegistration>("AuthenticateProviderNamesKey")
