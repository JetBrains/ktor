/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import org.gradle.api.initialization.Settings

fun Settings.enrichTeamCityData() {
    val ge = extensions.getByType(DevelocityConfiguration::class.java)

    gradle.projectsEvaluated {
        if (isCIRun) {
            val buildTypeId = "teamcity.buildType.id"
            val buildId = "teamcity.build.id"

            if (gradle.rootProject.hasProperty(buildId) && gradle.rootProject.hasProperty(buildTypeId)) {
                val buildIdValue = gradle.rootProject.property(buildId).toString()
                val teamCityBuildNumber = java.net.URLEncoder.encode(buildIdValue, "UTF-8")
                val teamCityBuildTypeId = gradle.rootProject.property(buildTypeId)

                ge.buildScan.link(
                    "Ktor TeamCity build",
                    "${TEAMCITY_URL}/buildConfiguration/${teamCityBuildTypeId}/${teamCityBuildNumber}"
                )
            }

            if (gradle.rootProject.hasProperty(buildId)) {
                ge.buildScan.value("CI build id", gradle.rootProject.property(buildId) as String)
            }
        }
    }
}

fun Settings.enrichGitData() {
    val ge = extensions.getByType(DevelocityConfiguration::class.java)

    val skipGitTags = settings.providers.gradleProperty("ktor.develocity.skipGitTags")
        .getOrElse("false")
        .toBooleanStrict()

    gradle.projectsEvaluated {
        if (!isCIRun && !skipGitTags) {
            // Git commit id
            val commitId = execute("git rev-parse --verify HEAD")
            if (commitId.isNotEmpty()) {
                ge.buildScan.value("Git Commit ID", commitId)
                ge.buildScan.link("GitHub Commit Link", "$GITHUB_REPO/tree/$commitId")
            }

            // Git branch name
            val branchName = execute("git rev-parse --abbrev-ref HEAD")
            if (branchName.isNotEmpty()) {
                ge.buildScan.value("Git Branch Name", branchName)
                ge.buildScan.link("GitHub Branch Link", "$GITHUB_REPO/tree/$branchName")
            }

            // Git dirty local state
            val status = execute("git status --porcelain")
            if (status.isNotEmpty()) {
                ge.buildScan.value("Git Status", status)
            }
        }
    }
}
