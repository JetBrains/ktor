/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.coroutines.*
import java.io.*
import java.nio.channels.*

public actual interface Selectable : Closeable, DisposableHandle {
    /**
     * Current selectable suspensions map
     */
    public val suspensions: InterestSuspensionsMap

    /**
     * Indicated if the selectable is closed.
     */
    public val isClosed: Boolean

    /**
     * current interests
     */
    public val interestedOps: Int

    public val channel: SelectableChannel

    /**
     * Apply [state] flag of [interest] to [interestedOps]. Notice that is doesn't actually change selection key.
     */
    public fun interestOp(interest: SelectInterest, state: Boolean)
}
