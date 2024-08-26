/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.util.pipeline.*

/**
 * A thin wrapper over pipelines and phases API. It usually wraps `pipeline.intercept(phase) { ... }`
 * statement.
 **/
internal class Interception<T : Any>(
    val phase: PipelinePhase,
    val action: (Pipeline<T, PipelineCall>) -> Unit
)

/**
 * An instance of [Interception] for the call phase, i.e. an [Interception] that takes place inside onCall { ... } handler.
 **/
internal typealias CallInterception = Interception<Unit>

/**
 * An instance of [Interception] for the receive phase, i.e. an [Interception] that takes place inside onCallReceive { ... } handler.
 **/
internal typealias ReceiveInterception = Interception<Any>

/**
 * An instance of [Interception] for the response phase, i.e. an [Interception] that takes place inside onCallRespond { ... } handler.
 **/
internal typealias ResponseInterception = Interception<Any>
