/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jte

import gg.jte.*
import gg.jte.output.*
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*

/**
 * A response content handled by the [Jte] plugin.
 *
 * @param template file name that is resolved by the Jte [TemplateEngine]
 * @param params to be passed to the template
 * @param etag value for the `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 */
public class JteContent(
    public val template: String,
    public val params: Map<String, Any?>,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * A configuration for the [Jte] plugin, where the Jte [TemplateEngine] can be customized.
 */
@KtorDsl
public class JteConfig {
    public lateinit var templateEngine: TemplateEngine
}

/**
 * A plugin that allows you to use jte templates as views within your application.
 * Provides the ability to respond with [JteContent].
 * You can learn more from [JTE](https://ktor.io/docs/jte.html).
 */
public val Jte: ApplicationPlugin<JteConfig> = createApplicationPlugin("jte", ::JteConfig) {

    val templateEngine = pluginConfig.templateEngine

    @OptIn(InternalAPI::class)
    on(BeforeResponseTransform(JteContent::class)) { _, content ->
        val writer = StringOutput()
        templateEngine.render(content.template, content.params, writer)

        val result = TextContent(writer.toString(), content.contentType)
        if (content.etag != null) {
            result.versions += EntityTagVersion(content.etag)
        }
        result
    }
}
