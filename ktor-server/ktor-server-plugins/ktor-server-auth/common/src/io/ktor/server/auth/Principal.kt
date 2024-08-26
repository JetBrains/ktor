/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import kotlin.reflect.*

/**
 * A marker interface indicating that a class represents credentials for authentication.
 */
@Deprecated("This interface can be safely removed")
public interface Credential

/**
 * A marker interface indicating that a class represents an authenticated principal.
 */
@Deprecated("This interface can be safely removed")
public interface Principal

internal class CombinedPrincipal {
    val principals: MutableList<Pair<String?, Any>> = mutableListOf()

    inline fun <reified T : Any> get(provider: String?): T? {
        return get(provider, T::class)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(provider: String?, klass: KClass<T>): T? {
        return principals
            .firstOrNull { (name, principal) ->
                if (provider != null) {
                    name == provider && klass.isInstance(principal)
                } else klass.isInstance(principal)
            }?.second as? T
    }

    fun add(provider: String?, principal: Any) {
        principals.add(Pair(provider, principal))
    }
}
