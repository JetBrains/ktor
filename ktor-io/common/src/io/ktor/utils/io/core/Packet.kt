/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("RedundantModalityModifier")

package io.ktor.utils.io.core

import kotlinx.io.*

/**
 * For streaming input it should be [Input.endOfInput] instead.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.core.isEmpty)
 */
@Deprecated("Use exhausted() instead", ReplaceWith("exhausted()"))
public val Source.isEmpty: Boolean
    get() = exhausted()

/**
 * For streaming input there is no reliable way to detect it without triggering bytes population from the underlying
 * source. Consider using [Input.endOfInput] or use [ByteReadPacket] instead.
 */
@Deprecated(
    "This makes no sense for streaming inputs. Some use-cases are covered by exhausted() method",
    ReplaceWith("!exhausted()")
)
public val Source.isNotEmpty: Boolean
    get() = !exhausted()
