/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.pebble

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.pebbletemplates.pebble.*
import java.io.*
import java.util.*

/**
 * Configuration for the [Pebble] plugin.
 */
@KtorDsl
public class PebbleConfiguration : PebbleEngine.Builder() {

    /**
     * Allows you to define currently available language translations
     * must follow IETF BCP 47 language tag string specification
     */
    public var availableLanguages: List<String>? = null
}

/**
 * A response content handled by the [Pebble] plugin.
 *
 * @param template name of the template to be resolved by Pebble
 * @param model which is passed into the template
 * @param locale which is used to resolve templates (optional)
 * @param etag value for `E-Tag` header (optional)
 * @param contentType response's content type which is set to `text/html;charset=utf-8` by default
 */
public class PebbleContent(
    public val template: String,
    public val model: Map<String, Any>,
    public val locale: Locale? = null,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * A plugin that allows you to use Pebble templates as views within your application.
 * Provides the ability to respond with [PebbleContent].
 * You can learn more from [Pebble](https://ktor.io/docs/pebble.html).
 */
public val Pebble: ApplicationPlugin<PebbleConfiguration> = createApplicationPlugin("Pebble", ::PebbleConfiguration) {
    val engine = pluginConfig.build()
    val availableLanguages: List<String>? = pluginConfig.availableLanguages?.toList()

    @OptIn(InternalAPI::class)
    on(BeforeResponseTransform(PebbleContent::class)) { call, content ->
        with(content) {
            val writer = StringWriter()
            var locale = locale

            if (availableLanguages != null && locale == null) {
                locale = call.request.acceptLanguageItems()
                    .firstOrNull { pluginConfig.availableLanguages!!.contains(it.value) }
                    ?.value?.let { Locale.forLanguageTag(it) }
            }
            engine.getTemplate(template).evaluate(writer, model, locale)

            val result = TextContent(text = writer.toString(), contentType)
            if (etag != null) {
                result.versions += EntityTagVersion(etag)
            }
            result
        }
    }
}
