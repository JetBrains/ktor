/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * Represents running execution of a pipeline
 * @param context object representing context in which pipeline executes
 * @param interceptors list of interceptors to execute
 * @param subject object representing subject that goes along the pipeline
 */
@KtorDsl
internal class DebugPipelineContext<TSubject : Any, TContext : Any>(
    context: TContext,
    private val interceptors: List<PipelineInterceptor<TSubject, TContext>>,
    subject: TSubject,
    override val coroutineContext: CoroutineContext
) : PipelineContext<TSubject, TContext>(context) {
    /**
     * Subject of this pipeline execution
     */
    override var subject: TSubject = subject

    private var index = 0

    /**
     * Finishes current pipeline execution
     */
    override fun finish() {
        index = -1
    }

    /**
     * Continues execution of the pipeline with the given subject
     */
    override suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    /**
     * Continues execution of the pipeline with the same subject
     */
    override suspend fun proceed(): TSubject {
        val index = index
        if (index < 0) return subject

        if (index >= interceptors.size) {
            finish()
            return subject
        }

        return proceedLoop()
    }

    override suspend fun execute(initial: TSubject): TSubject {
        index = 0
        subject = initial
        return proceed()
    }

    private suspend fun proceedLoop(): TSubject {
        do {
            val index = index
            if (index == -1) {
                break
            }
            val interceptors = interceptors
            if (index >= interceptors.size) {
                finish()
                break
            }
            val executeInterceptor = interceptors[index]
            this.index = index + 1
            executeInterceptor.invoke(this, subject)
        } while (true)

        return subject
    }
}
