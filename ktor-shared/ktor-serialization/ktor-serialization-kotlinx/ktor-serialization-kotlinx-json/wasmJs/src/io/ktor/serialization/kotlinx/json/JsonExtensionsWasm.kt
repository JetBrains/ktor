/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.json

import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*

internal actual suspend fun deserializeSequence(
    format: Json,
    content: ByteReadChannel,
    typeInfo: TypeInfo
): Sequence<Any?>? = null
