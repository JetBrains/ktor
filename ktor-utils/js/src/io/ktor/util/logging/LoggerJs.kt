// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

public enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
}

public actual interface Logger {
    public val level: LogLevel

    public actual fun error(message: String)
    public actual fun error(message: String, cause: Throwable)
    public actual fun warn(message: String)
    public actual fun warn(message: String, cause: Throwable)
    public actual fun info(message: String)
    public actual fun info(message: String, cause: Throwable)
    public actual fun debug(message: String)
    public actual fun debug(message: String, cause: Throwable)
    public actual fun trace(message: String)
    public actual fun trace(message: String, cause: Throwable)
}

public actual val Logger.isTraceEnabled: Boolean
    get() = true
