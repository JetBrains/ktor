/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.request

import io.ktor.server.application.*
import java.io.*

/**
 * Receives stream content for this call.
 * @return instance of [InputStream] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [InputStream].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.request.receiveStream)
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun ApplicationCall.receiveStream(): InputStream = receive()
