/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

internal actual fun getKtorEnvironmentProperties(): List<Pair<String, String>> = buildList {
    getEnvironmentKeys().forEach { key ->
        if (key.startsWith("ktor.")) {
            val value = getEnvironmentProperty(key) ?: return@forEach
            add(key to value)
        }
    }
}

internal actual fun getEnvironmentProperty(key: String): String? = js("process.env[key]")

internal actual fun setEnvironmentProperty(key: String, value: String): Unit = js("process.env[key] = value")

internal actual fun clearEnvironmentProperty(key: String): Unit = js("delete process.env[key]")

private fun getEnvironmentKeys(): Array<String> = js("Object.keys(process.env)").unsafeCast<Array<String>>()
