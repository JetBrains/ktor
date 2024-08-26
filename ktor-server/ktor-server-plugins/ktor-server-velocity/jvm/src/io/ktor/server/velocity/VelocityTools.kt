/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.velocity

import io.ktor.http.content.*
import io.ktor.server.application.*
import org.apache.velocity.app.*
import org.apache.velocity.tools.*
import org.apache.velocity.tools.config.*

private const val ENGINE_CONFIG_KEY = "ENGINE_CONFIG"

public fun EasyFactoryConfiguration.engine(configure: VelocityEngine.() -> Unit) {
    data(ENGINE_CONFIG_KEY, configure)
}

/**
 * A plugin that allows you to add standard and custom Velocity tools.
 * You can learn more from [Velocity](https://ktor.io/docs/velocity.html).
 */
@Suppress("UNCHECKED_CAST")
public val VelocityTools: ApplicationPlugin<EasyFactoryConfiguration> = createApplicationPlugin(
    "VelocityTools",
    ::EasyFactoryConfiguration
) {
    val engineConfig = pluginConfig.getData(ENGINE_CONFIG_KEY)
        ?.also { pluginConfig.removeData(it) }
        ?.value as (VelocityEngine.() -> Unit)? ?: {}

    val engine = VelocityEngine().apply(engineConfig)
    val toolManager = ToolManager().apply {
        this.configure(pluginConfig)
        velocityEngine = engine
    }

    fun process(content: VelocityContent): OutgoingContent {
        return velocityOutgoingContent(
            toolManager.velocityEngine.getTemplate(content.template),
            toolManager.createContext().also { it.putAll(content.model) },
            content.etag,
            content.contentType
        )
    }

    onCallRespond { _, value ->
        if (value is VelocityContent) {
            transformBody {
                process(value)
            }
        }
    }
}
