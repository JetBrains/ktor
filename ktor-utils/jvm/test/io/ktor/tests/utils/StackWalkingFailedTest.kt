/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.utils

import io.ktor.util.pipeline.*
import java.io.*
import kotlin.test.*

class StackWalkingFailedTest {
    @Test
    fun testInvocation() {
        assertFails {
            StackWalkingFailed.failedToCaptureStackFrame()
        }
    }

    @Test
    fun testStackTraceElement() {
        val element = StackWalkingFailedFrame.getStackTraceElement()
        assertNotNull(element)
        val foundClass = Class.forName(element.className)
        val foundMethod = foundClass.getMethod(element.methodName)
        val fileName = element.fileName
        assertNotNull(fileName)
        assertEquals(element.methodName, foundMethod.name)

        val file = File("common/src").walkTopDown()
            .filter { it.name == fileName }
            .singleOrNull() ?: error("File with name $fileName is not found in sources")

        val fileLines = file.readLines()

        fileLines.firstOrNull { it.trimStart().startsWith("package ${foundClass.getPackage().name}") }
            ?: fail("The returned file $fileName name should have the correct package definition")

        val pointedLine = fileLines.getOrElse(element.lineNumber - 1) {
            fail("The returned line number ${element.lineNumber} doesn't exist in $fileName")
        }

        assertTrue(
            "The returned line number ${element.lineNumber} doesn't point to " +
                "the declaration of function ${element.methodName}"
        ) {
            element.methodName in pointedLine && "fun " in pointedLine
        }
    }

    @Test
    fun fillStackTraceElement() {
        val element = StackWalkingFailedFrame.getStackTraceElement()
        val container = Exception("Expected exception.")
        container.stackTrace = arrayOf(element)

        val out = StringWriter()
        container.printStackTrace(PrintWriter(out, true))
        val trace = out.toString()

        assertTrue { container.message!! in trace }
        assertTrue { StackWalkingFailed::class.java.name in trace }
        assertTrue { StackWalkingFailed::failedToCaptureStackFrame.name in trace }

        assertTrue { "${element.fileName}:${element.lineNumber}" in trace }
    }
}
