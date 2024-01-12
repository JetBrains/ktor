/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.converters

import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.*

/**
 * Data conversion plugin to serialize and deserialize types using [converters] registry
 */
public class DataConversion(configuration: Configuration) : ConversionService {
    private val converters: Map<KClass<*>, ConversionService> = configuration.converters.toMap()

    override fun fromValues(values: List<String>, type: TypeInfo): Any? {
        if (values.isEmpty()) {
            return null
        }
        val converter = converters[type.type] ?: DefaultConversionService
        return converter.fromValues(values, type)
    }

    override fun toValues(value: Any?): List<String> {
        val type: KClass<*> = value?.let { it::class } ?: return listOf()
        val converter = converters[type] ?: DefaultConversionService
        return converter.toValues(value)
    }

    /**
     * Data conversion service configuration
     */
    @KtorDsl
    public class Configuration {
        internal val converters = mutableMapOf<KClass<*>, ConversionService>()

        /**
         * Register a [convertor] for [type] type
         */
        public fun convert(type: KClass<*>, convertor: ConversionService) {
            converters[type] = convertor
        }

        /**
         * Register and [configure] convertor for type [klass]
         */
        @Suppress("UNCHECKED_CAST")
        public fun <T : Any> convert(type: KType, configure: DelegatingConversionService.Configuration<T>.() -> Unit) {
            val klass = type.classifier as KClass<T>
            val configuration = DelegatingConversionService.Configuration(klass).apply(configure)

            val service = DelegatingConversionService(
                klass,
                configuration.decoder,
                configuration.encoder as ((Any?) -> List<String>)?
            )
            convert(klass, service)
        }

        /**
         * Register and [configure] convertor for reified type [T]
         */
        public inline fun <reified T : Any> convert(
            noinline configure: DelegatingConversionService.Configuration<T>.() -> Unit
        ): Unit = convert(typeOf<T>(), configure)
    }
}

/**
 * Implementation of [ConversionService] that delegates [fromValues] and [toValues] to [decoder] and [encoder]
 */
public class DelegatingConversionService(
    private val klass: KClass<*>,
    private val decoder: ((values: List<String>) -> Any?)?,
    private val encoder: ((value: Any?) -> List<String>)?,
) : ConversionService {

    override fun fromValues(values: List<String>, type: TypeInfo): Any? {
        val currentDecoder = decoder ?: throw IllegalStateException("Decoder was not specified for type '$klass'")
        return currentDecoder(values)
    }

    override fun toValues(value: Any?): List<String> {
        val currentDecoder = encoder ?: throw IllegalStateException("Encoder was not specified for type '$klass'")
        return currentDecoder(value)
    }

    /**
     * Custom convertor builder to be used in [DataConversion.Configuration]
     */
    public class Configuration<T : Any> @PublishedApi internal constructor(internal val klass: KClass<T>) {

        internal var decoder: ((values: List<String>) -> T)? = null
        internal var encoder: ((value: T) -> List<String>)? = null

        /**
         * Configure decoder function. Only one decoder could be supplied
         * @throws IllegalStateException
         */
        public fun decode(converter: (values: List<String>) -> T) {
            if (decoder != null) throw IllegalStateException("Decoder has already been set for type '$klass'")
            decoder = converter
        }

        /**
         * Configure encoder function. Only one encoder could be supplied
         * @throws IllegalStateException
         */
        public fun encode(converter: (value: T) -> List<String>) {
            if (encoder != null) throw IllegalStateException("Encoder has already been set for type '$klass'")
            encoder = converter
        }
    }
}
