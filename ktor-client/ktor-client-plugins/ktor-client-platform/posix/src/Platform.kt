/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.platform

internal actual object Platform {

    /**
     * Not supported on native.
     */
    fun isCleartextTrafficPermitted(hostname: String): Boolean = false
}
