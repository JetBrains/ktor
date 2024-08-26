/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.reflect.*

@Suppress("UNUSED")
internal actual interface CoroutineStackFrame {
    actual val callerFrame: CoroutineStackFrame?
    actual fun getStackTraceElement(): StackTraceElement?
}

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias StackTraceElement = Any

@Suppress("FunctionName")
internal actual fun createStackTraceElement(
    kClass: KClass<*>,
    methodName: String,
    fileName: String,
    lineNumber: Int
): StackTraceElement = Any()
