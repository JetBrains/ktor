/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal expect val DISABLE_SFG: Boolean

/**
 * Represents running execution of a pipeline
 *
 * @param context: object representing context in which pipeline executes
 */
@KtorDsl
public abstract class PipelineContext<TSubject : Any, TContext : Any>(
    public val context: TContext
) : CoroutineScope {

    /**
     * Subject of this pipeline execution that goes along the pipeline
     */
    public abstract var subject: TSubject

    /**
     * Finishes current pipeline execution
     */
    public abstract fun finish()

    /**
     * Continues execution of the pipeline with the given subject
     */
    public abstract suspend fun proceedWith(subject: TSubject): TSubject

    /**
     * Continues execution of the pipeline with the same subject
     */
    public abstract suspend fun proceed(): TSubject

    internal abstract suspend fun execute(initial: TSubject): TSubject
}

/**
 * Build a pipeline of the specified [interceptors] and create executor.
 */
internal fun <TSubject : Any, TContext : Any> pipelineContextFor(
    context: TContext,
    interceptors: List<PipelineInterceptor<TSubject, TContext>>,
    subject: TSubject,
    coroutineContext: CoroutineContext,
    debugMode: Boolean = false
): PipelineContext<TSubject, TContext> = if (DISABLE_SFG || debugMode) {
    DebugPipelineContext(context, interceptors, subject, coroutineContext)
} else {
    SuspendFunctionGun(subject, context, interceptors)
}
