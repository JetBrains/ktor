/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import io.ktor.utils.io.*

/**
 * This shouldn't be implemented directly. Inherit [Output] instead.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.utils.io.core.Output)
 */
@Deprecated(IO_DEPRECATION_MESSAGE, replaceWith = ReplaceWith("Sink", "kotlinx.io"))
public typealias Output = kotlinx.io.Sink
