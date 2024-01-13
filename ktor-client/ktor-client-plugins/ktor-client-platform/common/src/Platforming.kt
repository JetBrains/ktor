/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.platform

import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.host
import io.ktor.utils.io.errors.UnknownServiceException

public val Platforming: ClientPlugin<Any> = createClientPlugin("Platforming", ::Any) {
    onRequest { request, _ ->
        val host = request.host
        if (!Platform.isCleartextTrafficPermitted(host)) {
            throw UnknownServiceException(
                "CLEARTEXT communication to $host not permitted by network security policy"
            )
        }
    }
}
