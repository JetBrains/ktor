/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.internal

import kotlin.reflect.*

internal actual fun <T : Any> starProjectedTypeBridge(klass: KClass<T>): KType = ErasedType(klass)

internal data class ErasedType(private val klass: KClass<*>) : KType {
    override val arguments: List<KTypeProjection>
        get() = emptyList()

    override val classifier: KClassifier
        get() = klass

    override val isMarkedNullable: Boolean
        get() = false
}
