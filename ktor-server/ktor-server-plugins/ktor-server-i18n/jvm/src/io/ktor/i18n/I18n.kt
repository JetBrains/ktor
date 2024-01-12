/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.i18n

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import java.nio.charset.*

internal val I18N_LOGGER = KtorSimpleLogger("io.ktor.i18n")
internal val REQUIRED_RESPONSE_LANGUAGE = AttributeKey<String>("ResponseLanguage")

/**
 * I18n configuration. Currently supports [encoding] and [defaultLanguage]
 *
 * [defaultLanguage] must follow IETF BCP 47 language tag string specification
 */
public class I18nConfiguration {
    public var availableLanguages: List<String> = listOf("en-US")
    public var defaultLanguage: String = ""
}

/**
 * Represents I18n feature and its configuration.
 *
 * install(I18n) {
 *    defaultLanguage = "pt-BR"
 *    encoding = StandardCharsets.UTF_8
 * }
 */
public val I18n: ApplicationPlugin<I18nConfiguration> = createApplicationPlugin(
    "I18n",
    ::I18nConfiguration
) {
    val availableLanguages = pluginConfig.availableLanguages
    val defaultLanguage = pluginConfig.defaultLanguage

    onCall {
        val acceptedLanguages = it.request.acceptLanguageItems()

        val bestMatchLanguage = acceptedLanguages.firstOrNull {
            availableLanguages.contains(it.value)
        }?.value ?: defaultLanguage

        it.attributes.put(REQUIRED_RESPONSE_LANGUAGE, bestMatchLanguage)
    }

    onCallRespond { call ->
        val value = call.attributes[REQUIRED_RESPONSE_LANGUAGE]
        if (value.isNotBlank()) {
            call.response.header(HttpHeaders.ContentLanguage, value)
        }
    }
}
