/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

internal expect object Platform {

    fun isCleartextTrafficPermitted(hostname: String): Boolean
}
