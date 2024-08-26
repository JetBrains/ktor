/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.hsts

import kotlin.math.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * Duration to tell the client to keep the host in a list of known HSTS hosts.
 */
public var HSTSConfig.maxAgeDuration: Duration
    get() = maxAgeInSeconds.seconds
    set(newMaxAge) {
        require(!newMaxAge.isNegative()) { "Only non-negative durations can be specified" }
        maxAgeInSeconds = newMaxAge.toDouble(DurationUnit.SECONDS).roundToLong()
    }
