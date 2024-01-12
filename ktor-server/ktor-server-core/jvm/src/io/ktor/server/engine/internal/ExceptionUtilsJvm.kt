/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import java.util.concurrent.TimeoutException

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias ClosedChannelException = java.nio.channels.ClosedChannelException

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias OutOfMemoryError = java.lang.OutOfMemoryError

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias TimeoutException = TimeoutException
