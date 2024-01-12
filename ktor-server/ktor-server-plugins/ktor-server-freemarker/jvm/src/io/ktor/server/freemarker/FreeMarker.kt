/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.freemarker

import freemarker.template.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*
import java.io.*

/**
 * A response content handled by the [FreeMarker] plugin.
 *
 * @param template name that is resolved by FreeMarker
 * @param model to be passed during template rendering
 * @param etag value for the `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with the UTF-8 character encoding by default)
 */
public class FreeMarkerContent(
    public val template: String,
    public val model: Any?,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * A plugin that allows you to use FreeMarker templates as views within your application.
 * Provides the ability to respond with [FreeMarkerContent].
 * You can learn more from [FreeMarker](https://ktor.io/docs/freemarker.html).
 */
public val FreeMarker: ApplicationPlugin<Configuration> = createApplicationPlugin(
    "FreeMarker",
    { Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS) }
) {
    @OptIn(InternalAPI::class)
    on(BeforeResponseTransform(FreeMarkerContent::class)) { _, content ->
        with(content) {
            val writer = StringWriter()
            pluginConfig.getTemplate(template).process(model, writer)

            val result = TextContent(text = writer.toString(), contentType)
            if (etag != null) {
                result.versions += EntityTagVersion(etag)
            }

            result
        }
    }
}
