/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.system.*

/**
 * A plugin that allows you to configure a URL used to shut down the server. There are two ways to enable this plugin:
 * - In a HOCON configuration file.
 * - By installing the plugin.
 * You can learn more from [Shutdown URL](https://ktor.io/docs/shutdown-url.html).
 *
 * @property url to handle
 * @property exitCode is a function to compute a process exit code
 */
public class ShutDownUrl(public val url: String, public val exitCode: ApplicationCall.() -> Int) {
    /**
     * Shuts down an application using the specified [call].
     */
    public suspend fun doShutdown(call: ApplicationCall) {
        call.application.log.warn("Shutdown URL was called: server is going down")
        val application = call.application
        val environment = application.environment
        val exitCode = exitCode(call)

        val latch = CompletableDeferred<Nothing>()
        call.application.launch {
            latch.join()

            application.monitor.raise(ApplicationStopPreparing, environment)
            application.dispose()

            exitProcess(exitCode)
        }

        try {
            call.respond(HttpStatusCode.Gone)
        } finally {
            latch.cancel()
        }
    }

    /**
     * A plugin to install into an engine pipeline.
     */
    public object EnginePlugin : BaseApplicationPlugin<EnginePipeline, Config, ShutDownUrl> {
        override val key: AttributeKey<ShutDownUrl> = AttributeKey("shutdown.url")

        override fun install(pipeline: EnginePipeline, configure: Config.() -> Unit): ShutDownUrl {
            val config = Config()
            configure(config)

            val plugin = ShutDownUrl(config.shutDownUrl, config.exitCodeSupplier)
            pipeline.intercept(EnginePipeline.Before) {
                if (call.request.uri == plugin.url) {
                    plugin.doShutdown(call)
                }
            }

            return plugin
        }
    }

    /**
     * A configuration for the [ShutDownUrl] plugin.
     */
    @KtorDsl
    public class Config {
        /**
         * Specifies a URI used to handle a shutdown request.
         */
        public var shutDownUrl: String = "/ktor/application/shutdown"

        /**
         * A function that provides a process exit code by an application call.
         */
        public var exitCodeSupplier: ApplicationCall.() -> Int = { 0 }
    }

    public companion object {

        /**
         * An installation object of the [ShutDownUrl] plugin.
         */
        public val ApplicationCallPlugin: BaseApplicationPlugin<Application, Config, PluginInstance> =
            createApplicationPlugin("shutdown.url", ::Config) {
                val plugin = ShutDownUrl(pluginConfig.shutDownUrl, pluginConfig.exitCodeSupplier)

                onCall { call ->
                    if (call.request.uri == plugin.url) {
                        plugin.doShutdown(call)
                    }
                }
            }
    }
}
