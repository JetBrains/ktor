/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.mustache

import com.github.mustachejava.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*
import java.io.*

/**
 * A response content handled by the [Mustache] plugin.
 *
 * @param template name that is resolved by Mustache
 * @param model to be passed during template rendering
 * @param etag value for the `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with the UTF-8 character encoding by default)
 */
public class MustacheContent(
    public val template: String,
    public val model: Any?,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

@KtorDsl
public class MustacheConfig {
    public var mustacheFactory: MustacheFactory = DefaultMustacheFactory()
}

/**
 * A plugin that allows you to use Mustache templates as views within your application.
 * Provides the ability to respond with [MustacheContent].
 * You can learn more from [Mustache](https://ktor.io/docs/mustache.html).
 */
public val Mustache: ApplicationPlugin<MustacheConfig> = createApplicationPlugin("Mustache", ::MustacheConfig) {
    val mustacheFactory = pluginConfig.mustacheFactory

    @OptIn(InternalAPI::class)
    on(BeforeResponseTransform(MustacheContent::class)) { _, content ->
        with(content) {
            val writer = StringWriter()
            mustacheFactory.compile(content.template).execute(writer, model)

            val result = TextContent(text = writer.toString(), contentType)
            if (etag != null) {
                result.versions += EntityTagVersion(etag)
            }
            result
        }
    }
}
