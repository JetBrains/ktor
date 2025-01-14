/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.initialization.Settings

@Suppress("UnstableApiUsage")
fun Settings.execute(cmd: String): String {
    return settings.providers.exec {
        commandLine(cmd.split(" "))
    }.standardOutput.asText.get().trim()
}
