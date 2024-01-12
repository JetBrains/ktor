/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.api.publish.*
import org.gradle.api.publish.maven.*
import org.gradle.api.publish.maven.tasks.*
import org.gradle.jvm.tasks.*
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.*
import java.util.concurrent.locks.*

fun isAvailableForPublication(publication: Publication): Boolean {
    val name = publication.name
    if (name == "maven") return true

    var result = false
    val jvmAndCommon = setOf(
        "jvm",
        "androidRelease",
        "androidDebug",
        "js",
        "metadata",
        "kotlinMultiplatform"
    )
    result = result || name in jvmAndCommon
    result = result || (HOST_NAME == "linux" && (name == "linuxX64" || name == "linuxArm64"))
    result = result || (HOST_NAME == "windows" && name == "mingwX64")
    val macPublications = setOf(
        "iosX64",
        "iosArm64",
        "iosSimulatorArm64",

        "watchosX64",
        "watchosArm32",
        "watchosArm64",
        "watchosSimulatorArm64",

        "tvosX64",
        "tvosArm64",
        "tvosSimulatorArm64",

        "macosX64",
        "macosArm64"
    )

    result = result || (HOST_NAME == "macos" && name in macPublications)

    return result
}

fun Project.configurePublication() {
    if (COMMON_JVM_ONLY) return

    apply(plugin = "maven-publish")

    tasks.withType<AbstractPublishToMaven>().all {
        onlyIf { isAvailableForPublication(publication) }
    }

    val publishingUser: String? = System.getenv("PUBLISHING_USER")
    val publishingPassword: String? = System.getenv("PUBLISHING_PASSWORD")

    val repositoryId: String? = System.getenv("REPOSITORY_ID")
    val publishingUrl: String? = if (repositoryId?.isNotBlank() == true) {
        println("Set publishing to repository $repositoryId")
        "https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId"
    } else {
        System.getenv("PUBLISHING_URL")
    }

    val publishLocal: Boolean by rootProject.extra
    val globalM2: String by rootProject.extra
    val nonDefaultProjectStructure: List<String> by rootProject.extra

    val emptyJar = tasks.register<Jar>("emptyJar") {
        archiveAppendix.set("empty")
    }

    the<PublishingExtension>().apply {
        repositories {
            maven {
                if (publishLocal) {
                    setUrl(globalM2)
                } else {
                    publishingUrl?.let { setUrl(it) }
                    credentials {
                        username = publishingUser
                        password = publishingPassword
                    }
                }
            }
            maven {
                name = "testLocal"
                setUrl("$rootProject.buildDir/m2")
            }
        }

        publications.forEach {
            val publication = it as? MavenPublication ?: return@forEach
            publication.pom.withXml {
                val root = asNode()
                root.appendNode("name", project.name)
                root.appendNode(
                    "description",
                    "Ktor is a framework for quickly creating web applications in Kotlin with minimal effort."
                )
                root.appendNode("url", "https://github.com/ktorio/ktor")

                root.appendNode("licenses").apply {
                    appendNode("license").apply {
                        appendNode("name", "The Apache Software License, Version 2.0")
                        appendNode("url", "https://www.apache.org/licenses/LICENSE-2.0.txt")
                        appendNode("distribution", "repo")
                    }
                }

                root.appendNode("developers").apply {
                    appendNode("developer").apply {
                        appendNode("id", "JetBrains")
                        appendNode("name", "JetBrains Team")
                        appendNode("organization", "JetBrains")
                        appendNode("organizationUrl", "https://www.jetbrains.com")
                    }
                }

                root.appendNode("scm").apply {
                    appendNode("url", "https://github.com/ktorio/ktor.git")
                }
            }
        }

        if (nonDefaultProjectStructure.contains(project.name)) return@apply

        kotlin.targets.forEach { target ->
            val publication = publications.findByName(target.name) as? MavenPublication ?: return@forEach

            if (target.platformType.name == "jvm") {
                publication.artifact(emptyJar) {
                    classifier = "javadoc"
                }
            } else {
                publication.artifact(emptyJar) {
                    classifier = "javadoc"
                }
                publication.artifact(emptyJar) {
                    classifier = "kdoc"
                }
            }

            if (target.platformType.name == "native") {
                publication.artifact(emptyJar)
            }
        }
    }

    val publishToMavenLocal = tasks.getByName("publishToMavenLocal")
    tasks.getByName("publish").dependsOn(publishToMavenLocal)

    val signingKey = System.getenv("SIGN_KEY_ID")
    val signingKeyPassphrase = System.getenv("SIGN_KEY_PASSPHRASE")

    if (signingKey != null && signingKey != "") {
        extra["signing.gnupg.keyName"] = signingKey
        extra["signing.gnupg.passphrase"] = signingKeyPassphrase

        apply(plugin = "signing")

        the<SigningExtension>().apply {
            useGpgCmd()

            sign(the<PublishingExtension>().publications)
        }

        val gpgAgentLock: ReentrantLock by rootProject.extra { ReentrantLock() }

        tasks.withType<Sign> {
            doFirst {
                gpgAgentLock.lock()
            }

            doLast {
                gpgAgentLock.unlock()
            }
        }
    }

    val publishLinuxX64PublicationToMavenRepository = tasks.findByName("publishLinuxX64PublicationToMavenRepository")
    val signLinuxArm64Publication = tasks.findByName("signLinuxArm64Publication")
    if (publishLinuxX64PublicationToMavenRepository != null && signLinuxArm64Publication != null) {
        publishLinuxX64PublicationToMavenRepository.dependsOn(signLinuxArm64Publication)
    }

    val publishLinuxArm64PublicationToMavenRepository =
        tasks.findByName("publishLinuxArm64PublicationToMavenRepository")
    val signLinuxX64Publication = tasks.findByName("signLinuxX64Publication")
    if (publishLinuxArm64PublicationToMavenRepository != null && signLinuxX64Publication != null) {
        publishLinuxArm64PublicationToMavenRepository.dependsOn(signLinuxX64Publication)
    }
}
