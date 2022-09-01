/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

internal actual val DISABLE_CHAR_ARRAY_POOLING: Boolean =
    System.getProperty("ktor.internal.cio.disable.chararray.pooling")?.toBoolean() ?: false
