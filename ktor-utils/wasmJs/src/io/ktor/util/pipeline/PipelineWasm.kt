/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

internal actual fun <TSubject : Any, TContext : Any> pipelineStartCoroutineUninterceptedOrReturn(
    interceptor: PipelineInterceptor<TSubject, TContext>,
    context: PipelineContext<TSubject, TContext>,
    subject: TSubject,
    continuation: Continuation<Unit>
): Any? {
    val coroutine: suspend () -> Unit = { interceptor.invoke(context, subject) }
    return coroutine.startCoroutineUninterceptedOrReturn(continuation)
}
