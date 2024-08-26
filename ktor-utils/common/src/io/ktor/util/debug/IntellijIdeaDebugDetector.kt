/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.debug

internal expect object IntellijIdeaDebugDetector {
    /**
     * Checks whether Intellij Idea debugger is connected to the current Ktor server.
     * May return true only for JVM debugger.
     * */
    val isDebuggerConnected: Boolean
}
