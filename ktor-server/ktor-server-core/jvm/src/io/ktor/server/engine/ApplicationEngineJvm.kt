/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.util.concurrent.*

/**
 * Stops this [ApplicationEngine]
 *
 * @param gracePeriod the maximum amount of time for activity to cool down
 * @param timeout the maximum amount of time to wait until server stops gracefully
 * @param timeUnit the [TimeUnit] for [gracePeriod] and [timeout]
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun ApplicationEngine.stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
    stop(timeUnit.toMillis(gracePeriod), timeUnit.toMillis(timeout))
}
