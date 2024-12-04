/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.utils.io.*

/**
 * A hook that can be registered in [PluginBuilder].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.application.Hook)
 */
@KtorDsl
public interface Hook<HookHandler> {
    /**
     * Specifies how to install a hook in the [pipeline].
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.application.Hook.install)
     */
    public fun install(pipeline: ApplicationCallPipeline, handler: HookHandler)
}

internal class HookHandler<T>(private val hook: Hook<T>, private val handler: T) {
    fun install(pipeline: ApplicationCallPipeline) {
        hook.install(pipeline, handler)
    }
}
