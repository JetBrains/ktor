/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlin.reflect.*

internal expect class StackTraceElement

@Suppress("FunctionName")
internal expect fun createStackTraceElement(
    kClass: KClass<*>,
    methodName: String,
    fileName: String,
    lineNumber: Int
): StackTraceElement

internal expect interface CoroutineStackFrame {
    val callerFrame: CoroutineStackFrame?
    fun getStackTraceElement(): StackTraceElement?
}
