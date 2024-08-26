/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.plugins.logging.*
import kotlin.test.*

/**
 * Test logger that provides ability to verify it's content after test.
 * The [expectedLog] contains expected log entries
 * optionally prepended with control prefixes.
 * The following prefixes are supported:
 * - "???" means that the log entry is optional and could be missing
 * - "!!!" the log entry is flaky: it's required, but it's content is changing
 * - "+++" the log entry is required but the exact place is not known
 */

internal class TestLogger(
    private val dumpOnly: Boolean = false,
    block: LoggerDsl.() -> Unit
) : Logger {
    val dsl = LoggerDsl().apply(block)
    val log = mutableListOf<String>()
    private var matcher = dsl.build()

    constructor(vararg values: String) : this(false, {
        for (value in values) {
            when {
                value.startsWith("???") -> optional(value.drop(3))
                value.startsWith("!!!") -> changing(value.drop(3))
                value.startsWith("+++") -> somewhere(value.drop(3))
                else -> line(value)
            }
        }
    })

    override fun log(message: String) {
        if (dumpOnly) {
            log.addAll(message.lines())
            return
        }

        matcher.match(message)
    }

    fun reset() {
        matcher = dsl.build()
    }

    fun verify() {
        if (dumpOnly) {
            println(log.toString())
            return
        }

        matcher.finish()
    }
}

internal class CustomError(override val message: String) : Throwable() {
    override fun toString(): String = "CustomError[$message]"
}
