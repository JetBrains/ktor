/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * Obtain function FQName.
 */
internal fun Function<*>.methodName(): String {
    val method = (this as? KFunction<*>)?.javaMethod ?: return "${javaClass.name}.invoke"

    val clazz = method.declaringClass
    val name = method.name
    return "${clazz.name}.$name"
}
