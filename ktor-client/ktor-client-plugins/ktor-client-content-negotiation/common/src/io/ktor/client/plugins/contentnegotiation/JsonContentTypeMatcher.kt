/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation

import io.ktor.http.*

/**
 * Matcher that accepts all extended json content types
 */
public object JsonContentTypeMatcher : ContentTypeMatcher {
    override fun contains(contentType: ContentType): Boolean {
        if (contentType.match(ContentType.Application.Json)) {
            return true
        }

        val value = contentType.withoutParameters().toString()
        return value in ContentType.Application && value.endsWith("+json", ignoreCase = true)
    }
}
