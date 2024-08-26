// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.debug

internal actual object IntellijIdeaDebugDetector {
    actual val isDebuggerConnected: Boolean = false
}
