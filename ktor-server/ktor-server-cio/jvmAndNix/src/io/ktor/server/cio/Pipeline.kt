/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.http.cio.*
import io.ktor.server.cio.backend.*
import kotlinx.coroutines.*
import kotlin.native.concurrent.*

/**
 * HTTP request handler function
 */
public typealias HttpRequestHandler = suspend ServerRequestScope.(request: Request) -> Unit

internal val HttpPipelineCoroutine: CoroutineName = CoroutineName("http-pipeline")
internal val HttpPipelineWriterCoroutine: CoroutineName = CoroutineName("http-pipeline-writer")
internal val RequestHandlerCoroutine: CoroutineName = CoroutineName("request-handler")
