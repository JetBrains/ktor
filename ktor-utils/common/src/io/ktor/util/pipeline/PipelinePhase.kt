/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

/**
 * Represents a phase in a pipeline
 *
 * @param name a name for this phase
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.pipeline.PipelinePhase)
 */
public class PipelinePhase(public val name: String) {
    override fun toString(): String = "Phase('$name')"
}

/**
 * An exception about misconfigured phases in a pipeline
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.pipeline.InvalidPhaseException)
 */
public class InvalidPhaseException(message: String) : Throwable(message)
