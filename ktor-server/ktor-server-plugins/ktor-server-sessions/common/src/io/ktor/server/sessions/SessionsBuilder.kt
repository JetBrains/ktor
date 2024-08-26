/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sessions

import io.ktor.util.reflect.*
import kotlin.reflect.*

/**
 * Configures [Sessions] to pass a session identifier in cookies using the [name] `Set-Cookie` attribute and
 * store the serialized session's data in the server [storage].
 */
public inline fun <reified S : Any> SessionsConfig.cookie(name: String, storage: SessionStorage) {
    cookie<S>(name, typeInfo<S>(), storage)
}

/**
 * Configures [Sessions] to pass a session identifier in cookies using the [name] `Set-Cookie` attribute and
 * store the serialized session's data in the server [storage].
 */
public fun <S : Any> SessionsConfig.cookie(name: String, typeInfo: TypeInfo, storage: SessionStorage) {
    @Suppress("UNCHECKED_CAST")
    val sessionType = typeInfo.type as KClass<S>

    val builder = CookieIdSessionBuilder(sessionType, typeInfo.kotlinType!!)
    cookie(name, builder, sessionType, storage)
}

@PublishedApi
internal fun <S : Any> SessionsConfig.cookie(
    name: String,
    builder: CookieIdSessionBuilder<S>,
    sessionType: KClass<S>,
    storage: SessionStorage
) {
    val transport = SessionTransportCookie(name, builder.cookie, builder.transformers)
    val tracker = SessionTrackerById(sessionType, builder.serializer, storage, builder.sessionIdProvider)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

/**
 * Configures [Sessions] to pass a session identifier in cookies using the [name] `Set-Cookie` attribute and
 * store the serialized session's data in the server [storage].
 * The [block] parameter allows you to configure additional cookie settings, for example:
 * - add other cookie attributes;
 * - sign and encrypt session data.
 */
public inline fun <reified S : Any> SessionsConfig.cookie(
    name: String,
    storage: SessionStorage,
    noinline block: CookieIdSessionBuilder<S>.() -> Unit
) {
    cookie(name, typeInfo<S>(), storage, block)
}

/**
 * Configures [Sessions] to pass a session identifier in cookies using the [name] `Set-Cookie` attribute and
 * store the serialized session's data in the server [storage].
 * The [block] parameter allows you to configure additional cookie settings, for example:
 * - add other cookie attributes;
 * - sign and encrypt session data.
 */
public fun <S : Any> SessionsConfig.cookie(
    name: String,
    typeInfo: TypeInfo,
    storage: SessionStorage,
    block: CookieIdSessionBuilder<S>.() -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    val sessionType = typeInfo.type as KClass<S>

    val builder = CookieIdSessionBuilder(sessionType, typeInfo.kotlinType!!).apply(block)
    cookie(name, builder, sessionType, storage)
}

/**
 * Configures [Sessions] to pass a session identifier in a [name] HTTP header and
 * store the serialized session's data in the server [storage].
 */
public inline fun <reified S : Any> SessionsConfig.header(name: String, storage: SessionStorage) {
    header<S>(name, typeInfo<S>(), storage)
}

/**
 * Configures [Sessions] to pass a session identifier in a [name] HTTP header and
 * store the serialized session's data in the server [storage].
 */
public fun <S : Any> SessionsConfig.header(name: String, typeInfo: TypeInfo, storage: SessionStorage) {
    header<S>(name, typeInfo, storage) {}
}

/**
 * Configures [Sessions] to pass a session identifier in a [name] HTTP header and
 * store the serialized session's data in the server [storage].
 * The [block] parameter allows you to configure additional settings, for example, sign and encrypt session data.
 */
public inline fun <reified S : Any> SessionsConfig.header(
    name: String,
    storage: SessionStorage,
    noinline block: HeaderIdSessionBuilder<S>.() -> Unit
) {
    header(name, typeInfo<S>(), storage, block)
}

/**
 * Configures [Sessions] to pass a session identifier in a [name] HTTP header and
 * store the serialized session's data in the server [storage].
 * The [block] parameter allows you to configure additional settings, for example, sign and encrypt session data.
 */
public fun <S : Any> SessionsConfig.header(
    name: String,
    typeInfo: TypeInfo,
    storage: SessionStorage,
    block: HeaderIdSessionBuilder<S>.() -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    val sessionType = typeInfo.type as KClass<S>

    val builder = HeaderIdSessionBuilder(sessionType, typeInfo.kotlinType!!).apply(block)
    header(name, sessionType, storage, builder)
}

@PublishedApi
internal fun <S : Any> SessionsConfig.header(
    name: String,
    sessionType: KClass<S>,
    storage: SessionStorage?,
    builder: HeaderSessionBuilder<S>
) {
    val transport = SessionTransportHeader(name, builder.transformers)
    val tracker = when {
        storage != null && builder is HeaderIdSessionBuilder<S> -> SessionTrackerById(
            sessionType,
            builder.serializer,
            storage,
            builder.sessionIdProvider
        )

        else -> SessionTrackerByValue(sessionType, builder.serializer)
    }
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

/**
 * Configures [Sessions] to pass the serialized session's data in cookies using the [name] `Set-Cookie` attribute.
 */
public inline fun <reified S : Any> SessionsConfig.cookie(name: String) {
    cookie<S>(name, typeInfo<S>())
}

/**
 * Configures [Sessions] to pass the serialized session's data in cookies using the [name] `Set-Cookie` attribute.
 */
public fun <S : Any> SessionsConfig.cookie(name: String, typeInfo: TypeInfo) {
    @Suppress("UNCHECKED_CAST")
    val sessionType = typeInfo.type as KClass<S>

    val builder = CookieSessionBuilder(sessionType, typeInfo.kotlinType!!)
    cookie(name, sessionType, builder)
}

/**
 * Configures [Sessions] to pass the serialized session's data in cookies using the [name] `Set-Cookie` attribute.
 * The [block] parameter allows you to configure additional cookie settings, for example:
 * - add other cookie attributes;
 * - sign and encrypt session data.
 *
 * For example, the code snippet below shows how to specify a cookie's path and expiration time:
 * ```kotlin
 * install(Sessions) {
 *     cookie<UserSession>("user_session") {
 *         cookie.path = "/"
 *         cookie.maxAgeInSeconds = 10
 *     }
 * }
 * ```
 */
public inline fun <reified S : Any> SessionsConfig.cookie(
    name: String,
    noinline block: CookieSessionBuilder<S>.() -> Unit
) {
    cookie(name, typeInfo<S>(), block)
}

/**
 * Configures [Sessions] to pass the serialized session's data in cookies using the [name] `Set-Cookie` attribute.
 * The [block] parameter allows you to configure additional cookie settings, for example:
 * - add other cookie attributes;
 * - sign and encrypt session data.
 *
 * For example, the code snippet below shows how to specify a cookie's path and expiration time:
 * ```kotlin
 * install(Sessions) {
 *     cookie<UserSession>("user_session") {
 *         cookie.path = "/"
 *         cookie.maxAgeInSeconds = 10
 *     }
 * }
 * ```
 */
public fun <S : Any> SessionsConfig.cookie(
    name: String,
    typeInfo: TypeInfo,
    block: CookieSessionBuilder<S>.() -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    val sessionType = typeInfo.type as KClass<S>

    val builder = CookieSessionBuilder(sessionType, typeInfo.kotlinType!!).apply(block)
    cookie(name, sessionType, builder)
}

@PublishedApi
internal fun <S : Any> SessionsConfig.cookie(
    name: String,
    sessionType: KClass<S>,
    builder: CookieSessionBuilder<S>
) {
    val transport = SessionTransportCookie(name, builder.cookie, builder.transformers)
    val tracker = SessionTrackerByValue(sessionType, builder.serializer)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

/**
 * Configures [Sessions] to pass the serialized session's data in a [name] HTTP header.
 *
 * In the example below, session data will be passed to the client using the `cart_session` custom header.
 * ```kotlin
 * install(Sessions) {
 *     header<CartSession>("cart_session")
 * }
 * ```
 * On the client side, you need to append this header to each request to get session data.
 */
public inline fun <reified S : Any> SessionsConfig.header(name: String) {
    header<S>(name, typeInfo<S>())
}

/**
 * Configures [Sessions] to pass the serialized session's data in a [name] HTTP header.
 *
 * In the example below, session data will be passed to the client using the `cart_session` custom header.
 * ```kotlin
 * install(Sessions) {
 *     header<CartSession>("cart_session")
 * }
 * ```
 * On the client side, you need to append this header to each request to get session data.
 */
public fun <S : Any> SessionsConfig.header(name: String, typeInfo: TypeInfo) {
    header<S>(name, typeInfo) {}
}

/**
 * Configures [Sessions] to pass the serialized session's data in a [name] HTTP header.
 * The [block] parameter allows you to configure additional settings, for example, sign and encrypt session data.
 */
public inline fun <reified S : Any> SessionsConfig.header(
    name: String,
    noinline block: HeaderSessionBuilder<S>.() -> Unit
) {
    header(name, typeInfo<S>(), block)
}

/**
 * Configures [Sessions] to pass the serialized session's data in a [name] HTTP header.
 * The [block] parameter allows you to configure additional settings, for example, sign and encrypt session data.
 */
public fun <S : Any> SessionsConfig.header(
    name: String,
    typeInfo: TypeInfo,
    block: HeaderSessionBuilder<S>.() -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    val sessionType = typeInfo.type as KClass<S>

    val builder = HeaderSessionBuilder(sessionType, typeInfo.kotlinType!!).apply(block)
    header(name, sessionType, null, builder)
}

/**
 * A configuration that allows you to configure additional cookie settings for [Sessions], for example:
 * - add cookie attributes;
 * - sign and encrypt session data.
 */
public class CookieIdSessionBuilder<S : Any> @PublishedApi internal constructor(
    type: KClass<S>,
    typeInfo: KType
) : CookieSessionBuilder<S>(type, typeInfo) {

    /**
     * Registers a function used to generate a session ID.
     */
    public fun identity(f: () -> String) {
        sessionIdProvider = f
    }

    /**
     * A function used to provide a current session ID.
     */
    public var sessionIdProvider: () -> String = { generateSessionId() }
        private set
}

/**
 * A configuration that allows you to configure additional cookie settings for [Sessions].
 * @property type - session instance type
 */
public open class CookieSessionBuilder<S : Any>
@PublishedApi
internal constructor(
    public val type: KClass<S>,
    public val typeInfo: KType
) {

    /**
     * Specifies a serializer used to serialize session data.
     */
    public var serializer: SessionSerializer<S> = defaultSessionSerializer(typeInfo)

    private val _transformers = mutableListOf<SessionTransportTransformer>()

    /**
     * Gets transformers used to sign and encrypt session data.
     */
    public val transformers: List<SessionTransportTransformer> get() = _transformers

    /**
     * Registers a [transformer] used to sign and encrypt session data.
     */
    public fun transform(transformer: SessionTransportTransformer) {
        _transformers.add(transformer)
    }

    /**
     * Gets a configuration used to specify additional cookie attributes for [Sessions].
     */
    public val cookie: CookieConfiguration = CookieConfiguration()
}

/**
 * A configuration that allows you to configure header settings for [Sessions].
 * @property type session instance type
 */
public open class HeaderSessionBuilder<S : Any>
@PublishedApi
internal constructor(
    public val type: KClass<S>,
    public val typeInfo: KType
) {

    /**
     * Specifies a serializer used to serialize session data.
     */
    public var serializer: SessionSerializer<S> = defaultSessionSerializer(typeInfo)

    private val _transformers = mutableListOf<SessionTransportTransformer>()

    /**
     * Gets transformers used to sign and encrypt session data.
     */
    public val transformers: List<SessionTransportTransformer> get() = _transformers

    /**
     * Registers a [transformer] used to sign and encrypt session data.
     */
    public fun transform(transformer: SessionTransportTransformer) {
        _transformers.add(transformer)
    }
}

/**
 * A configuration that allows you to configure header settings for [Sessions].
 */
public class HeaderIdSessionBuilder<S : Any>
@PublishedApi
internal constructor(
    type: KClass<S>,
    typeInfo: KType
) : HeaderSessionBuilder<S>(type, typeInfo) {

    /**
     * Registers a function used to generate a session ID.
     */
    public fun identity(f: () -> String) {
        sessionIdProvider = f
    }

    /**
     * A function used to provide a current session ID.
     */
    public var sessionIdProvider: () -> String = { generateSessionId() }
        private set
}
