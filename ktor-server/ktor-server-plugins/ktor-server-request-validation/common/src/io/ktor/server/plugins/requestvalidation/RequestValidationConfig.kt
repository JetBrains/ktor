/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.requestvalidation

import kotlin.reflect.*

/**
 * A config for [RequestValidation] plugin
 */
public class RequestValidationConfig {

    internal val validators: MutableList<Validator> = mutableListOf()

    internal var validateContentLength: Boolean = false

    /**
     * Enables validation of the request body length matches the [Content-Length] header.
     * If the length doesn't match, body channel will be cancelled with [IOException].
     */
    public fun validateContentLength() {
        validateContentLength = true
    }

    /**
     * Registers [validator]
     */
    public fun validate(validator: Validator) {
        validators.add(validator)
    }

    /**
     * Registers [Validator] that should check instances of a [kClass] using [block]
     */
    public fun <T : Any> validate(kClass: KClass<T>, block: suspend (T) -> ValidationResult) {
        val validator = object : Validator {
            @Suppress("UNCHECKED_CAST")
            override suspend fun validate(value: Any): ValidationResult = block(value as T)
            override fun filter(value: Any): Boolean = kClass.isInstance(value)
        }
        validate(validator)
    }

    /**
     * Registers [Validator] that should check instances of a [T] using [block]
     */
    public inline fun <reified T : Any> validate(noinline block: suspend (T) -> ValidationResult) {
        // `KClass.isInstance` doesn't work for JS, but direct `value is T` works
        val validator = object : Validator {
            override suspend fun validate(value: Any): ValidationResult = block(value as T)
            override fun filter(value: Any): Boolean = value is T
        }
        validate(validator)
    }

    /**
     * Registers [Validator] using DSL
     * ```
     * validate {
     *    filter { it is Int }
     *    validation { check(it is Int); ... }
     * }
     * ```
     */
    public fun validate(block: ValidatorBuilder.() -> Unit) {
        val builder = ValidatorBuilder().apply(block)
        validate(builder.build())
    }

    public class ValidatorBuilder {
        private lateinit var validationBlock: suspend (Any) -> ValidationResult
        private lateinit var filterBlock: (Any) -> Boolean

        public fun filter(block: (Any) -> Boolean) {
            filterBlock = block
        }

        public fun validation(block: suspend (Any) -> ValidationResult) {
            validationBlock = block
        }

        internal fun build(): Validator {
            check(::validationBlock.isInitialized) { "`validation { ... } block is not set`" }
            check(::filterBlock.isInitialized) { "`filter { ... } block is not set`" }
            return object : Validator {
                override suspend fun validate(value: Any) = validationBlock(value)
                override fun filter(value: Any): Boolean = filterBlock(value)
            }
        }
    }
}
