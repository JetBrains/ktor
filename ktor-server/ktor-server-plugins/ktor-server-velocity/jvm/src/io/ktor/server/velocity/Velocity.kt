/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.velocity

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*
import org.apache.velocity.*
import org.apache.velocity.app.*
import org.apache.velocity.context.*
import java.io.*

/**
 * A response content handled by the [io.ktor.server.velocity.Velocity] plugin.
 *
 * @param template name to be resolved by Velocity
 * @param model to be passed during template rendering
 * @param etag value for the `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with the UTF-8 character encoding by default)
 */
public class VelocityContent(
    public val template: String,
    public val model: Map<String, Any>,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

internal fun velocityOutgoingContent(
    template: Template,
    model: Context,
    etag: String?,
    contentType: ContentType
): OutgoingContent {
    val writer = StringWriter()
    template.merge(model, writer)

    val result = TextContent(text = writer.toString(), contentType)
    if (etag != null) {
        result.versions += EntityTagVersion(etag)
    }
    return result
}

/**
 * A plugin that allows you to use Velocity templates as views within your application.
 * Provides the ability to respond with [VelocityContent].
 * You can learn more from [Velocity](https://ktor.io/docs/velocity.html).
 */
public val Velocity: ApplicationPlugin<VelocityEngine> = createApplicationPlugin("Velocity", ::VelocityEngine) {

    pluginConfig.init()

    @OptIn(InternalAPI::class)
    on(BeforeResponseTransform(VelocityContent::class)) { _, content ->
        velocityOutgoingContent(
            pluginConfig.getTemplate(content.template),
            VelocityContext(content.model),
            content.etag,
            content.contentType
        )
    }
}
