/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

internal actual suspend fun PipelineContext<Any, PipelineCall>.defaultPlatformTransformations(
    query: Any
): Any? = null

internal actual fun PipelineContext<*, PipelineCall>.multiPartData(rc: ByteReadChannel): MultiPartData =
    error("Multipart is not supported in native")

internal actual fun ByteReadPacket.readTextWithCustomCharset(charset: Charset): String =
    error("Charset $charset is not supported in native")
