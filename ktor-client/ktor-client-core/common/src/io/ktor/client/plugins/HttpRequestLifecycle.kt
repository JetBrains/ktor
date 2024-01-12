/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpRequestLifecycle")

/**
 * A client's HTTP plugin that sets up [HttpRequestBuilder.executionContext] and completes it when the pipeline is fully
 * processed.
 */
public val HttpRequestLifecycle: ClientPlugin<Unit> = createClientPlugin("RequestLifecycle") {
    on(SetupRequestContext) { request, proceed ->
        val executionContext = SupervisorJob(request.executionContext)

        attachToClientEngineJob(executionContext, client.coroutineContext[Job]!!)

        try {
            request.executionContext = executionContext
            proceed()
        } catch (cause: Throwable) {
            executionContext.completeExceptionally(cause)
            throw cause
        } finally {
            executionContext.complete()
        }
    }
}

public object SetupRequestContext : ClientHook<suspend (HttpRequestBuilder, suspend () -> Unit) -> Unit> {
    override fun install(client: HttpClient, handler: suspend (HttpRequestBuilder, suspend () -> Unit) -> Unit) {
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            handler(context, ::proceed)
        }
    }
}

/**
 * Attach client engine job.
 */
private fun attachToClientEngineJob(
    requestJob: CompletableJob,
    clientEngineJob: Job
) {
    val handler = clientEngineJob.invokeOnCompletion { cause ->
        if (cause != null) {
            LOGGER.trace("Cancelling request because engine Job failed with error: $cause")
            requestJob.cancel("Engine failed", cause)
        } else {
            LOGGER.trace("Cancelling request because engine Job completed")
            requestJob.complete()
        }
    }

    requestJob.invokeOnCompletion {
        handler.dispose()
    }
}
