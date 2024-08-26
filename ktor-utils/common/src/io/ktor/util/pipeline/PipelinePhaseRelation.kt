/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

/**
 * Represents relations between pipeline phases
 */
internal sealed class PipelinePhaseRelation {
    /**
     * Given phase should be executed after [relativeTo] phase
     * @property relativeTo represents phases for relative positioning
     */
    class After(val relativeTo: PipelinePhase) : PipelinePhaseRelation()

    /**
     * Given phase should be executed before [relativeTo] phase
     * @property relativeTo represents phases for relative positioning
     */
    class Before(val relativeTo: PipelinePhase) : PipelinePhaseRelation()

    /**
     * Given phase should be executed last
     */
    data object Last : PipelinePhaseRelation()
}
