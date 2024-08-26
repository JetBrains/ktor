/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.routing

import io.ktor.server.routing.*
import kotlin.test.*

internal class RouteSelectorTest {

    @Test
    fun testEvaluateWithPrefixAndSuffixMatched() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false
        )

        assertTrue(evaluation is RouteSelectorEvaluation.Success)
        assertEquals(evaluation.quality, RouteSelectorEvaluation.qualityParameterWithPrefixOrSuffix)
        assertEquals(evaluation.succeeded, true)
        assertEquals(evaluation.parameters["param"], "PARAM")
    }

    @Test
    fun testEvaluateWithPrefixNotMatched() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("1prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.FailedPath)
    }

    @Test
    fun testEvaluateWithSuffixNotMatched() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix1"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.FailedPath)
    }

    @Test
    fun testEvaluateWithoutPrefixOrSuffix() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("PARAM"),
            segmentIndex = 0,
            name = "param",
            isOptional = false
        )

        assertTrue(evaluation is RouteSelectorEvaluation.Success)
        assertEquals(evaluation.succeeded, true)
        assertEquals(evaluation.quality, RouteSelectorEvaluation.qualityParameter)
    }

    @Test
    fun testEvaluateWithSegmentIndexOutsideOfSegments() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 1,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.FailedPath)
    }

    @Test
    fun testEvaluateWithPrefixNotMatchedOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("1prefixPARAMsuffix"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun testEvaluateWithSuffixNotMatchedOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix1"),
            segmentIndex = 0,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun testEvaluateWithSegmentIndexOutsideOfSegmentsOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("prefixPARAMsuffix"),
            segmentIndex = 1,
            name = "param",
            prefix = "prefix",
            suffix = "suffix",
            isOptional = true
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun testEvaluateWithTrailingSlashAndOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("foo", ""),
            segmentIndex = 1,
            name = "param",
            isOptional = true
        )

        assertEquals(
            evaluation,
            RouteSelectorEvaluation
                .Success(RouteSelectorEvaluation.qualityMissing, segmentIncrement = 1)
        )
    }

    @Test
    fun testEvaluateWithoutTrailingSlashAndOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("foo"),
            segmentIndex = 1,
            name = "param",
            isOptional = true
        )

        assertEquals(evaluation, RouteSelectorEvaluation.Missing)
    }

    @Test
    fun testEvaluateWithTrailingSlashAndNonOptional() {
        val evaluation = evaluatePathSegmentParameter(
            segments = listOf("foo", ""),
            segmentIndex = 1,
            name = "param",
            isOptional = false
        )

        assertEquals(evaluation, RouteSelectorEvaluation.FailedPath)
    }
}
