/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.i18n

import io.ktor.server.routing.*
import java.util.*

/**
 * Translate a message key to an accepted language specified in HTTP request
 */
public fun RoutingContext.i18n(key: String): String {
    val bestMatchLanguage = call.attributes[REQUIRED_RESPONSE_LANGUAGE]

    val locale = Locale.forLanguageTag(bestMatchLanguage)

    val bundle = ResourceBundle.getBundle("messages/messages", locale)
    val value = bundle.getString(key)

    I18N_LOGGER.debug("translating to $locale: $key=$value")
    return value
}
