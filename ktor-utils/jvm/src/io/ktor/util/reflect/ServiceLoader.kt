/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import io.ktor.utils.io.*
import java.util.*

// NOTE: ServiceLoader should use specific call convention to be optimized by R8 on Android:
// `ServiceLoader.load(X.class, X.class.getClassLoader()).iterator()`
// See: https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java

/**
 * Loads all implementations of the service [T] using [ServiceLoader.load].
 * @see loadServiceOrNull
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.reflect.loadServices)
 */
@InternalAPI
public inline fun <reified T : Any> loadServices(): List<T> = ServiceLoader.load(
    T::class.java,
    T::class.java.classLoader,
).iterator().asSequence().toList()

/**
 * Loads single implementation of the service [T] using [ServiceLoader.load]
 * returns `null` if there are no any implementations.
 * @see loadServices
 */
@InternalAPI
public inline fun <reified T : Any> loadServiceOrNull(): T? = ServiceLoader.load(
    T::class.java,
    T::class.java.classLoader,
).iterator().asSequence().firstOrNull()
