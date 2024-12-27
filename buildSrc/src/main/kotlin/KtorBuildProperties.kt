/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

val IDEA_ACTIVE: Boolean = System.getProperty("idea.active") == "true"

val OS_NAME = System.getProperty("os.name").lowercase()

val HOST_NAME = when {
    OS_NAME.startsWith("linux") -> "linux"
    OS_NAME.startsWith("windows") -> "windows"
    OS_NAME.startsWith("mac") -> "macos"
    else -> error("Unknown os name `$OS_NAME`")
}
