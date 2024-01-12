/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.thymeleaf

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*
import org.thymeleaf.*
import org.thymeleaf.context.*
import java.util.*

/**
 * A response content handled by the [io.ktor.server.thymeleaf.Thymeleaf] plugin.
 *
 * @param template name that is resolved by Thymeleaf
 * @param model to be passed during template rendering
 * @param etag value for the `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 * @param locale object represents a specific geographical, political, or cultural region
 * @param fragments names from the [template] that is resolved by Thymeleaf
 */
public class ThymeleafContent(
    public val template: String,
    public val model: Map<String, Any>,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8),
    public val locale: Locale = Locale.getDefault(),
    public val fragments: Set<String> = setOf()
)

/**
 * A plugin that allows you to use Thymeleaf templates as views within your application.
 * Provides the ability to respond with [ThymeleafContent].
 * You can learn more from [Thymeleaf](https://ktor.io/docs/thymeleaf.html).
 */
public val Thymeleaf: ApplicationPlugin<TemplateEngine> = createApplicationPlugin(
    "Thymeleaf",
    ::TemplateEngine
) {
    @OptIn(InternalAPI::class)
    on(BeforeResponseTransform(ThymeleafContent::class)) { _, content ->
        with(content) {
            val context = Context(locale).apply { setVariables(model) }

            val result = TextContent(
                text = pluginConfig.process(template, fragments, context),
                contentType
            )
            if (etag != null) {
                result.versions += EntityTagVersion(etag)
            }
            result
        }
    }
}
