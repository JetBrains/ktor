/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.utils.io.*

/**
 * A hook that can be registered in [ClientPluginBuilder].
 */
@KtorDsl
public interface ClientHook<HookHandler> {

    /**
     * Specifies how to install a hook into [client].
     */
    public fun install(client: HttpClient, handler: HookHandler)
}

internal class HookHandler<T>(private val hook: ClientHook<T>, private val handler: T) {
    fun install(client: HttpClient) {
        hook.install(client, handler)
    }
}
