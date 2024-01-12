/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.pipeline

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual val DISABLE_SFG: Boolean = getenv("KTOR_INTERNAL_DISABLE_SFG")?.toKString() == "true"
