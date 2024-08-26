/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import kotlin.coroutines.*

@Suppress("UNCHECKED_CAST")
internal actual fun <TSubject : Any, TContext : Any> pipelineStartCoroutineUninterceptedOrReturn(
    interceptor: PipelineInterceptor<TSubject, TContext>,
    context: PipelineContext<TSubject, TContext>,
    subject: TSubject,
    continuation: Continuation<Unit>
): Any? = (interceptor as PipelineInterceptorCoroutine<TSubject, TContext>)(context, subject, continuation)
