/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors

import kotlin.math.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * Duration to tell the client to keep CORS options.
 */
public var CORSConfig.maxAgeDuration: Duration
    get() = maxAgeInSeconds.seconds
    set(newMaxAge) {
        require(!newMaxAge.isNegative()) { "Only non-negative durations can be specified" }
        maxAgeInSeconds = newMaxAge.toDouble(DurationUnit.SECONDS).roundToLong()
    }
