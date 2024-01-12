/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ktlint-disable experimental:argument-list-wrapping
package io.ktor.server.sessions

import io.ktor.http.*
import io.ktor.server.sessions.serialization.*
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.lang.reflect.*
import java.math.*
import java.util.*
import java.util.concurrent.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

private const val TYPE_TOKEN_PARAMETER_NAME: String = "\$type"

/**
 * A reflection-based session serializer. Can be used for backward compatibility with previous versions.
 * A serialized format is textual and optimized for size as it could be transferred via HTTP headers or cookies.
 *
 * @property T is a session instance class handled by this serializer
 */
public inline fun <reified T : Any> reflectionSessionSerializer(): SessionSerializer<T> =
    reflectionSessionSerializer(typeOf<T>())

/**
 * A reflection-based session serializer. Can be used for backward compatibility with previous versions.
 * A serialized format is textual and optimized for size as it could be transferred via HTTP headers or cookies.
 *
 * @property typeInfo is a session instance class handled by this serializer
 */
public fun <T : Any> reflectionSessionSerializer(typeInfo: KType): SessionSerializer<T> =
    SessionSerializerReflection(typeInfo)

/**
 * A reflection-based session serializer.
 * A serialized format is textual and optimized for size as it could be transferred via HTTP headers or cookies.
 *
 * @property type is a session instance class handled by this serializer
 */
internal class SessionSerializerReflection<T : Any>(
    typeInfo: KType
) : SessionSerializer<T> {

    @Suppress("UNCHECKED_CAST")
    val type: KClass<T> = typeInfo.jvmErasure as KClass<T>

    override fun deserialize(text: String): T {
        val values = parseQueryString(text)

        @Suppress("UNCHECKED_CAST")
        if (type == Parameters::class) {
            return values as T
        }

        return deserializeObject(type, text)
    }

    override fun serialize(session: T): String {
        if (type == Parameters::class) {
            return (session as Parameters).formUrlEncode()
        }
        val typed = session.cast(type)
        return serializeClassInstance(typed)
    }

    private fun <T : Any> newInstance(type: KClass<T>, bundle: StringValues): T {
        type.objectInstance?.let { return it }

        val constructor = findConstructor(type, bundle)

        val params = constructor
            .parameters
            .associateBy(
                { it },
                {
                    when (it.kind) {
                        KParameter.Kind.INSTANCE,
                        KParameter.Kind.EXTENSION_RECEIVER -> findParticularType(type, bundle)

                        KParameter.Kind.VALUE ->
                            coerceType(it.type, deserializeValue(it.type.jvmErasure, bundle[it.name!!]!!))
                    }
                }
            )

        return constructor.callBy(params)
    }

    private fun <T : Any> findParticularType(type: KClass<T>, bundle: StringValues): KClass<out T> {
        if (type.isSealed) {
            val typeToken = bundle[TYPE_TOKEN_PARAMETER_NAME] ?: error("No typeToken found for sealed $type")
            return type.sealedSubclasses.firstOrNull { it.simpleName == typeToken }
                ?: error("No sealed subclass $typeToken found in $type")
        }
        if (type.isAbstract) {
            error("Abstract types are not supported: $type")
        }

        return type
    }

    private fun <T : Any> findConstructor(type: KClass<T>, bundle: StringValues): KFunction<T> {
        if (type.isSealed) {
            val particularType = findParticularType(type, bundle)
            val filtered = bundle.filter { key, _ -> key != TYPE_TOKEN_PARAMETER_NAME }
            return findConstructor(particularType, filtered)
        }
        if (type.isAbstract) {
            error("Abstract types are not supported: $type")
        }

        bundle[TYPE_TOKEN_PARAMETER_NAME]?.let { typeName ->
            require(type.simpleName == typeName)
        }

        if (type.objectInstance != null) {
            val getter = KClass<T>::objectInstance
            @Suppress("UNCHECKED_CAST")
            return getter.getter as KFunction<T>
        }

        return type.constructors
            .filter { it.parameters.all { parameter -> parameter.name != null && parameter.name!! in bundle } }
            .maxByOrNull { it.parameters.size }
            ?: throw IllegalArgumentException("Couldn't instantiate $type for parameters ${bundle.names()}")
    }

    private fun <X> assignValue(instance: X, p: KProperty1<X, *>, value: Any?) {
        val originalValue = p.get(instance)

        when {
            isListType(p.returnType) -> when {
                value !is List<*> -> assignValue(instance, p, coerceType(p.returnType, value))
                p is KMutableProperty1<X, *> -> p.setter.call(instance, coerceType(p.returnType, value))
                originalValue is MutableList<*> -> {
                    originalValue.withUnsafe {
                        clear()
                        addAll(value)
                    }
                }

                else -> throw IllegalStateException("Couldn't inject property ${p.name} from value $value")
            }

            isSetType(p.returnType) -> when {
                value !is Set<*> -> assignValue(instance, p, coerceType(p.returnType, value))
                p is KMutableProperty1<X, *> -> p.setter.call(instance, coerceType(p.returnType, value))
                originalValue is MutableSet<*> -> {
                    originalValue.withUnsafe {
                        clear()
                        addAll(value)
                    }
                }

                else -> throw IllegalStateException("Couldn't inject property ${p.name} from value $value")
            }

            isMapType(p.returnType) -> when {
                value !is Map<*, *> -> assignValue(instance, p, coerceType(p.returnType, value))
                p is KMutableProperty1<X, *> -> p.setter.call(instance, coerceType(p.returnType, value))
                originalValue is MutableMap<*, *> -> {
                    originalValue.withUnsafe {
                        clear()
                        putAll(value)
                    }
                }

                else -> throw IllegalStateException("Couldn't inject property ${p.name} from value $value")
            }

            p is KMutableProperty1<X, *> -> when {
                value == null && !p.returnType.isMarkedNullable ->
                    throw IllegalArgumentException("Couldn't inject null to property ${p.name}")

                else -> p.setter.call(instance, coerceType(p.returnType, value))
            }

            else -> {
            }
        }
    }

    private fun coerceType(type: KType, value: Any?): Any? =
        when {
            value == null -> null
            isListType(type) -> when {
                value !is List<*> && value is Iterable<*> -> coerceType(type, value.toList())
                value !is List<*> -> throw IllegalArgumentException(
                    "Couldn't coerce type ${value::class.java} to $type"
                )

                else -> {
                    val contentType = type.arguments.single().type
                        ?: throw IllegalArgumentException(
                            "Star projections are not supported for list element: ${type.arguments[0]}"
                        )

                    listOf(type.toJavaClass().kotlin, ArrayList::class)
                        .toTypedList<MutableList<*>>()
                        .filterAssignable(type)
                        .firstHasNoArgConstructor()
                        ?.callNoArgConstructor()
                        ?.withUnsafe { addAll(value.map { coerceType(contentType, it) }); this }
                        ?: throw IllegalArgumentException("Couldn't coerce type ${value::class.java} to $type")
                }
            }

            isSetType(type) -> when {
                value !is Set<*> && value is Iterable<*> -> coerceType(type, value.toSet())
                value !is Set<*> -> throw IllegalArgumentException("Couldn't coerce type ${value::class.java} to $type")

                else -> {
                    val contentType = type.arguments.single().type
                        ?: throw IllegalArgumentException(
                            "Star projections are not supported for set element: ${type.arguments[0]}"
                        )

                    listOf(type.toJavaClass().kotlin, LinkedHashSet::class, HashSet::class, TreeSet::class)
                        .toTypedList<MutableSet<*>>()
                        .filterAssignable(type)
                        .firstHasNoArgConstructor()
                        ?.callNoArgConstructor()
                        ?.withUnsafe { addAll(value.map { coerceType(contentType, it) }); this }
                        ?: throw IllegalArgumentException("Couldn't coerce type ${value::class.java} to $type")
                }
            }

            isMapType(type) -> when (value) {
                !is Map<*, *> -> throw IllegalArgumentException("Couldn't coerce type ${value::class.java} to $type")
                else -> {
                    val keyType = type.arguments[0].type
                        ?: throw IllegalArgumentException(
                            "Star projections are not supported for map key: ${type.arguments[0]}"
                        )

                    val valueType = type.arguments[1].type
                        ?: throw IllegalArgumentException(
                            "Star projections are not supported for map value ${type.arguments[1]}"
                        )

                    listOf(
                        type.toJavaClass().kotlin,
                        LinkedHashMap::class,
                        HashMap::class,
                        TreeMap::class,
                        ConcurrentHashMap::class
                    )
                        .toTypedList<MutableMap<*, *>>()
                        .filterAssignable(type)
                        .firstHasNoArgConstructor()
                        ?.callNoArgConstructor()
                        ?.withUnsafe {
                            putAll(
                                value.mapKeys { coerceType(keyType, it.key) }.mapValues {
                                    coerceType(
                                        valueType,
                                        it.value
                                    )
                                }
                            )
                            this
                        }
                        ?: throw IllegalArgumentException("Couldn't coerce type ${value::class.java} to $type")
                }
            }

            isEnumType(type) -> {
                type.javaType.toJavaClass().enumConstants.first { (it as? Enum<*>)?.name == value }
            }

            type.toJavaClass() == Float::class.java && value is Number -> value.toFloat()
            type.toJavaClass() == UUID::class.java && value is String -> UUID.fromString(value)
            else -> value
        }

    private inline fun <R> MutableList<*>.withUnsafe(block: MutableList<Any?>.() -> R): R {
        // it is potentially dangerous however it would be too slow to check every element
        @Suppress("UNCHECKED_CAST")
        return with(this as MutableList<Any?>, block)
    }

    private inline fun <R> MutableSet<*>.withUnsafe(block: MutableSet<Any?>.() -> R): R {
        // it is potentially dangerous however it would be too slow to check every element
        @Suppress("UNCHECKED_CAST")
        return with(this as MutableSet<Any?>, block)
    }

    private inline fun <R> MutableMap<*, *>.withUnsafe(block: MutableMap<Any?, Any?>.() -> R): R {
        // it is potentially dangerous however it would be too slow to check every element
        @Suppress("UNCHECKED_CAST")
        return with(this as MutableMap<Any?, Any?>, block)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> List<KClass<*>>.toTypedList() = this as List<KClass<T>>

    private fun KType.toJavaClass() = javaType.toJavaClass()

    private fun Type.toJavaClass(): Class<*> =
        when (this) {
            is ParameterizedType -> this.rawType.toJavaClass()
            is Class<*> -> this
            else -> throw IllegalArgumentException("Bad type $this")
        }

    private fun <T : Any> List<KClass<T>>.filterAssignable(type: KType): List<KClass<T>> =
        filter { type.toJavaClass().isAssignableFrom(it.java) }

    private fun <T : Any> List<KClass<T>>.firstHasNoArgConstructor() =
        firstOrNull { clazz -> clazz.constructors.any { it.parameters.isEmpty() } }

    private fun <T : Any> KClass<T>.callNoArgConstructor() = constructors.first { it.parameters.isEmpty() }.call()

    @Suppress("IMPLICIT_CAST_TO_ANY")
    private fun deserializeValue(owner: KClass<*>, value: String): Any? =
        if (!value.startsWith("#")) {
            throw IllegalArgumentException("Bad serialized value")
        } else {
            when (value.getOrNull(1)) {
                null, 'n' -> null
                'i' -> value.drop(2).toInt()
                'l' -> value.drop(2).toLong()
                'f' -> value.drop(2).toDouble()
                'b' -> when (value.getOrNull(2)) {
                    'o' -> when (value.getOrNull(3)) {
                        't' -> true
                        'f' -> false
                        else -> throw IllegalArgumentException("Unsupported bo-value ${value.take(4)}")
                    }

                    'd' -> BigDecimal(value.drop(3))
                    'i' -> BigInteger(value.drop(3))
                    else -> throw IllegalArgumentException("Unsupported b-type ${value.take(3)}")
                }

                'o' -> when (value.getOrNull(2)) {
                    'm' -> Optional.empty<Any?>()
                    'p' -> Optional.ofNullable(deserializeValue(owner, value.drop(3)))
                    else -> throw IllegalArgumentException("Unsupported o-value ${value.take(3)}")
                }

                's' -> value.drop(2)
                'c' -> when (value.getOrNull(2)) {
                    'l' -> deserializeCollection(value.drop(3))
                    's' -> deserializeCollection(value.drop(3)).toSet()
                    'h' -> value.drop(3).first()
                    else -> throw IllegalArgumentException("Unsupported c-type ${value.take(3)}")
                }

                'm' -> deserializeMap(value.drop(2))
                '#' -> deserializeObject(owner, value.drop(2))
                else -> throw IllegalArgumentException("Unsupported type ${value.take(2)}")
            }
        }

    private fun serializeValue(value: Any?): String =
        when (value) {
            null -> "#n"
            is Int -> "#i$value"
            is Long -> "#l$value"
            is Float -> "#f$value"
            is Double -> "#f$value"
            is Boolean -> "#bo${value.toString().first()}"
            is Char -> "#ch$value"
            is BigDecimal -> "#bd$value"
            is BigInteger -> "#bi$value"
            is Optional<*> -> when {
                value.isPresent -> "#op${serializeValue(value.get())}"
                else -> "#om"
            }

            is String -> "#s$value"
            is List<*> -> "#cl${serializeCollection(value)}"
            is Set<*> -> "#cs${serializeCollection(value)}"
            is Map<*, *> -> "#m${serializeMap(value)}"
            is Enum<*> -> "#s${value.name}"
            is UUID -> "#s$value"
            else -> "##${serializeClassInstance(value)}"
        }

    private fun <T : Any> serializeClassInstance(value: T): String {
        @Suppress("UNCHECKED_CAST")
        val type = value::class as KClass<T>

        var bundle = type.memberProperties.sortedBy { it.name }.map { p ->
            p.name to serializeValue(p.get(value))
        }

        if (type.simpleName != null && type.superclasses.any { it.isSealed }) {
            bundle += Pair(TYPE_TOKEN_PARAMETER_NAME, type.simpleName!!)
        }

        return bundle.formUrlEncode()
    }

    private fun <Y : Any> deserializeObject(type: KClass<Y>, encoded: String): Y {
        val bundle = parseQueryString(encoded)
        val instance = newInstance(type, bundle)

        for (p in type.memberProperties) {
            val encodedValue = bundle[p.name]
            if (encodedValue != null) {
                val value = deserializeValue(p.returnType.jvmErasure, encodedValue)
                val coerced = coerceType(p.returnType, value)
                assignValue(instance, p, coerced)
            }
        }

        return instance
    }

    private fun deserializeCollection(value: String): List<*> = value
        .decodeURLQueryComponent()
        .split("&")
        .filter { it.isNotEmpty() }
        .map { deserializeValue(Any::class, it.decodeURLQueryComponent()) }

    private fun serializeCollection(value: Collection<*>): String = value
        .joinToString("&") { serializeValue(it).encodeURLQueryComponent() }
        .encodeURLQueryComponent()

    private fun deserializeMap(value: String): Map<*, *> = value
        .decodeURLQueryComponent()
        .split("&")
        .filter { it.isNotEmpty() }
        .associateBy(
            { deserializeValue(Any::class, it.substringBefore('=').decodeURLQueryComponent()) },
            { deserializeValue(Any::class, it.substringAfter('=').decodeURLQueryComponent()) }
        )

    private fun serializeMap(value: Map<*, *>): String = value
        .map {
            serializeValue(it.key).encodeURLQueryComponent() +
                "=" + serializeValue(it.value).encodeURLQueryComponent()
        }
        .joinToString("&")
        .encodeURLQueryComponent()

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun isListType(type: KType): Boolean {
        return getRawType(type)?.let { java.util.List::class.java.isAssignableFrom(it) } ?: false
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun isSetType(type: KType): Boolean {
        return getRawType(type)?.let { java.util.Set::class.java.isAssignableFrom(it) } ?: false
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun isEnumType(type: KType): Boolean {
        return getRawType(type)?.let { java.lang.Enum::class.java.isAssignableFrom(it) } ?: false
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun isMapType(type: KType): Boolean {
        return getRawType(type)?.let { java.util.Map::class.java.isAssignableFrom(it) } ?: false
    }

    private fun getRawType(type: KType): Class<*>? = type.javaType.let { javaType ->
        when (javaType) {
            is ParameterizedType -> javaType.rawType as? Class<*>
            is Class<*> -> javaType
            else -> null
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Any.cast(type: KClass<T>) =
    if (type.java.isInstance(this)) this as T else throw ClassCastException("${this::class} couldn't be cast to $type")
