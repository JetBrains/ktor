/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlinx-atomicfu") {
                useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${requested.version}")
            }
        }
    }
}

rootProject.name = "ktor"

val native_targets_enabled = !extra.has("disable_native_targets")
val CACHE_USER = System.getenv("GRADLE_CACHE_USER")

if (CACHE_USER != null) {
    val CACHE_PASSWORD = System.getenv("GRADLE_CACHE_PASSWORD")
    buildCache {
        remote(HttpBuildCache::class) {
            isPush = true
            setUrl("https://ktor-gradle-cache.teamcity.com/cache/")
            credentials {
                username = CACHE_USER
                password = CACHE_PASSWORD
            }
        }
    }
}

val fullVersion = System.getProperty("java.version", "8.0.0")
val versionComponents = fullVersion
    .split(".")
    .take(2)
    .filter { it.isNotBlank() }
    .map { Integer.parseInt(it) }

val currentJdk = if (versionComponents[0] == 1) versionComponents[1] else versionComponents[0]

include(":ktor-server")
include(":ktor-server:ktor-server-core")
include(":ktor-server:ktor-server-config-yaml")
include(":ktor-server:ktor-server-tests")
include(":ktor-server:ktor-server-host-common")
include(":ktor-server:ktor-server-test-host")
include(":ktor-server:ktor-server-test-suites")
include(":ktor-server:ktor-server-jetty")
include(":ktor-server:ktor-server-jetty:ktor-server-jetty-test-http2")
include(":ktor-server:ktor-server-servlet")
include(":ktor-server:ktor-server-tomcat")
include(":ktor-server:ktor-server-netty")
include(":ktor-server:ktor-server-cio")
include(":ktor-client")
include(":ktor-client:ktor-client-core")
include(":ktor-client:ktor-client-tests")
include(":ktor-client:ktor-client-apache")
include(":ktor-client:ktor-client-apache5")
include(":ktor-client:ktor-client-android")
include(":ktor-client:ktor-client-cio")
if (native_targets_enabled) {
    include(":ktor-client:ktor-client-curl")
    include(":ktor-client:ktor-client-ios")
    include(":ktor-client:ktor-client-darwin")
    include(":ktor-client:ktor-client-darwin-legacy")
    include(":ktor-client:ktor-client-winhttp")
}
if (currentJdk >= 11) {
    include(":ktor-client:ktor-client-java")
    include(":ktor-client:ktor-client-jetty-jakarta")
    include(":ktor-server:ktor-server-servlet-jakarta")
    include(":ktor-server:ktor-server-jetty-jakarta")
    include(":ktor-server:ktor-server-jetty-jakarta:ktor-server-jetty-test-http2-jakarta")
    include(":ktor-server:ktor-server-tomcat-jakarta")
}
include(":ktor-client:ktor-client-jetty")
include(":ktor-client:ktor-client-js")
include(":ktor-client:ktor-client-mock")
include(":ktor-client:ktor-client-okhttp")
include(":ktor-client:ktor-client-plugins")
include(":ktor-client:ktor-client-plugins:ktor-client-json")
include(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-gson")
include(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-jackson")
include(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-serialization")
include(":ktor-client:ktor-client-plugins:ktor-client-auth")
include(":ktor-client:ktor-client-plugins:ktor-client-call-id")
include(":ktor-client:ktor-client-plugins:ktor-client-logging")
include(":ktor-client:ktor-client-plugins:ktor-client-platform")
include(":ktor-client:ktor-client-plugins:ktor-client-encoding")
include(":ktor-client:ktor-client-plugins:ktor-client-websockets")
include(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation")
include(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation:ktor-client-content-negotiation-tests")
include(":ktor-client:ktor-client-plugins:ktor-client-resources")
include(":ktor-client:ktor-client-plugins:ktor-client-bom-remover")
include(":ktor-server:ktor-server-plugins:ktor-server-auth")
include(":ktor-server:ktor-server-plugins:ktor-server-auth-jwt")
include(":ktor-server:ktor-server-plugins:ktor-server-auth-ldap")
include(":ktor-server:ktor-server-plugins:ktor-server-auto-head-response")
include(":ktor-server:ktor-server-plugins:ktor-server-body-limit")
include(":ktor-server:ktor-server-plugins:ktor-server-caching-headers")
include(":ktor-server:ktor-server-plugins:ktor-server-call-id")
include(":ktor-server:ktor-server-plugins:ktor-server-call-logging")
include(":ktor-server:ktor-server-plugins:ktor-server-compression")
include(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers")
include(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation")
include(":ktor-server:ktor-server-plugins:ktor-server-cors")
include(":ktor-server:ktor-server-plugins:ktor-server-csrf")
include(":ktor-server:ktor-server-plugins:ktor-server-data-conversion")
include(":ktor-server:ktor-server-plugins:ktor-server-default-headers")
include(":ktor-server:ktor-server-plugins:ktor-server-double-receive")
include(":ktor-server:ktor-server-plugins:ktor-server-forwarded-header")
include(":ktor-server:ktor-server-plugins:ktor-server-freemarker")
include(":ktor-server:ktor-server-plugins:ktor-server-hsts")
include(":ktor-server:ktor-server-plugins:ktor-server-html-builder")
include(":ktor-server:ktor-server-plugins:ktor-server-http-redirect")
include(":ktor-server:ktor-server-plugins:ktor-server-jte")
include(":ktor-server:ktor-server-plugins:ktor-server-metrics")
include(":ktor-server:ktor-server-plugins:ktor-server-metrics-micrometer")
include(":ktor-server:ktor-server-plugins:ktor-server-mustache")
include(":ktor-server:ktor-server-plugins:ktor-server-partial-content")
include(":ktor-server:ktor-server-plugins:ktor-server-pebble")
include(":ktor-server:ktor-server-plugins:ktor-server-rate-limit")
include(":ktor-server:ktor-server-plugins:ktor-server-request-validation")
include(":ktor-server:ktor-server-plugins:ktor-server-resources")
include(":ktor-server:ktor-server-plugins:ktor-server-sessions")
include(":ktor-server:ktor-server-plugins:ktor-server-status-pages")
include(":ktor-server:ktor-server-plugins:ktor-server-thymeleaf")
include(":ktor-server:ktor-server-plugins:ktor-server-velocity")
include(":ktor-server:ktor-server-plugins:ktor-server-webjars")
include(":ktor-server:ktor-server-plugins:ktor-server-websockets")
include(":ktor-server:ktor-server-plugins:ktor-server-method-override")
include(":ktor-server:ktor-server-plugins:ktor-server-openapi")
include(":ktor-server:ktor-server-plugins:ktor-server-swagger")
include(":ktor-server:ktor-server-plugins:ktor-server-sse")
include(":ktor-server:ktor-server-plugins:ktor-server-i18n")
include(":ktor-server:ktor-server-plugins")
include(":ktor-http")
include(":ktor-http:ktor-http-cio")
include(":ktor-io")
include(":ktor-utils")
include(":ktor-network")
include(":ktor-network:ktor-network-tls")
include(":ktor-network:ktor-network-tls:ktor-network-tls-certificates")
include(":ktor-bom")
include(":ktor-test-dispatcher")
include(":ktor-shared")
include(":ktor-shared:ktor-call-id")
include(":ktor-shared:ktor-resources")
include(":ktor-shared:ktor-serialization")
include(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx")
include(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-tests")
include(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-json")
include(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-cbor")
include(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-xml")
include(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-protobuf")
include(":ktor-shared:ktor-serialization:ktor-serialization-gson")
include(":ktor-shared:ktor-serialization:ktor-serialization-jackson")
include(":ktor-shared:ktor-serialization:ktor-serialization-tests")
include(":ktor-shared:ktor-events")
include(":ktor-shared:ktor-websocket-serialization")
include(":ktor-shared:ktor-websockets")
include(":ktor-shared:ktor-sse")
include(":ktor-shared:ktor-junit")
include(":ktor-java-modules-test")
