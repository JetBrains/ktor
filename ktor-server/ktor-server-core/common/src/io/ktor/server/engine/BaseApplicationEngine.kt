/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Base class for implementing [ApplicationEngine]
 *
 * It creates default engine pipeline, provides [application] property and installs default transformations
 * on respond and receive
 *
 * @param environment instance of [ApplicationEnvironment] for this engine
 * @param pipeline pipeline to use with this engine
 */
public abstract class BaseApplicationEngine(
    public final override val environment: ApplicationEnvironment,
    protected val monitor: Events,
    developmentMode: Boolean,
    public val pipeline: EnginePipeline = defaultEnginePipeline(environment.config, developmentMode)
) : ApplicationEngine {

    /**
     * Configuration for the [BaseApplicationEngine].
     */
    public open class Configuration : ApplicationEngine.Configuration()

    protected val resolvedConnectorsDeferred: CompletableDeferred<List<EngineConnectorConfig>> = CompletableDeferred()

    init {
        val environment = environment
        val info = StartupInfo()
        val pipeline = pipeline

        BaseApplicationResponse.setupSendPipeline(pipeline.sendPipeline)

        monitor.subscribe(ApplicationStarting) {
            if (!info.isFirstLoading) {
                info.initializedStartAt = getTimeMillis()
            }
            it.receivePipeline.merge(pipeline.receivePipeline)
            it.sendPipeline.merge(pipeline.sendPipeline)
            it.receivePipeline.installDefaultTransformations()
            it.sendPipeline.installDefaultTransformations()
            it.installDefaultInterceptors()
            it.installDefaultTransformationChecker()
        }
        monitor.subscribe(ApplicationStarted) {
            val finishedAt = getTimeMillis()
            val elapsedTimeInSeconds = (finishedAt - info.initializedStartAt) / 1_000.0
            if (info.isFirstLoading) {
                environment.log.info("Application started in $elapsedTimeInSeconds seconds.")
                info.isFirstLoading = false
            } else {
                environment.log.info("Application auto-reloaded in $elapsedTimeInSeconds seconds.")
            }
        }
    }

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> {
        return resolvedConnectorsDeferred.await()
    }
}

private suspend fun PipelineContext<Unit, PipelineCall>.verifyHostHeader() {
    val hostHeaders = call.request.headers.getAll(HttpHeaders.Host) ?: return
    if (hostHeaders.size > 1) {
        call.respond(HttpStatusCode.BadRequest)
        finish()
    }
}

private class StartupInfo {
    var isFirstLoading = true
    var initializedStartAt = getTimeMillis()
}

@OptIn(InternalAPI::class)
private fun Application.installDefaultInterceptors() {
    intercept(ApplicationCallPipeline.Fallback) {
        if (call.isHandled) return@intercept

        val status = call.response.status()
            ?: call.attributes.getOrNull(RoutingFailureStatusCode)
            ?: HttpStatusCode.NotFound

        call.respond(status)
    }

    intercept(ApplicationCallPipeline.Call) {
        verifyHostHeader()
    }
}

private fun Application.installDefaultTransformationChecker() {
    // Respond with "415 Unsupported Media Type" if content cannot be transformed on receive
    intercept(ApplicationCallPipeline.Plugins) {
        try {
            proceed()
        } catch (e: CannotTransformContentToTypeException) {
            call.respond(HttpStatusCode.UnsupportedMediaType)
        }
    }

    val checkBodyPhase = PipelinePhase("BodyTransformationCheckPostRender")
    sendPipeline.insertPhaseAfter(ApplicationSendPipeline.Render, checkBodyPhase)
    sendPipeline.intercept(checkBodyPhase) { subject ->
        if (subject !is OutgoingContent) {
            proceedWith(HttpStatusCodeContent(HttpStatusCode.NotAcceptable))
        }
    }
}
