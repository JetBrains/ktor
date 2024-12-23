/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.util.*

plugins {
    id("com.gradle.develocity")
    id("com.gradle.common-custom-user-data-gradle-plugin")
}

develocity {
    val startParameter = gradle.startParameter
    val scanJournal = File(settingsDir, "scan-journal.log")

    server = DEVELOCITY_SERVER

    buildScan {
        uploadInBackground = !isCIRun

        // obfuscate NIC since we don't want to expose user real IP (will be relevant without VPN)
        obfuscation {
            ipAddresses { addresses -> addresses.map { _ -> "0.0.0.0" } }
        }

        capture {
            fileFingerprints = true
        }

        buildScanPublished {
            scanJournal.appendText("${Date()} — $buildScanUri — $startParameter\n")
        }

        val skipBuildScans = settings.providers.gradleProperty("ktor.develocity.skipBuildScans")
            .getOrElse("false")
            .toBooleanStrict()

        publishing.onlyIf { !skipBuildScans }
    }
}

buildCache {
    if (isCIRun) {
        local {
            isEnabled = false
        }
    }

    remote(develocity.buildCache) {
        isPush = isCIRun
        isEnabled = true
    }
}

enrichTeamCityData()
enrichGitData()
