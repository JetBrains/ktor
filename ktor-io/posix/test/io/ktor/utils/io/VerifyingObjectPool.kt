/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.pool.*
import kotlin.experimental.*
import kotlin.native.*

@OptIn(ExperimentalNativeApi::class)
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun identityHashCode(instance: Any): Int = instance.identityHashCode()
