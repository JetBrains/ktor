/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

/**
 * [Logging]  log level.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.logging.LogLevel)
 */
public enum class LogLevel(
    public val info: Boolean,
    public val headers: Boolean,
    public val body: Boolean
) {
    ALL(true, true, true),
    HEADERS(true, true, false),
    BODY(true, false, true),
    INFO(true, false, false),
    NONE(false, false, false)
}
