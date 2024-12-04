/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation

import io.ktor.http.*

/**
 * Matcher that accepts all extended json content types
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.plugins.contentnegotiation.JsonContentTypeMatcher)
 */
public object JsonContentTypeMatcher : ContentTypeMatcher {
    override fun contains(contentType: ContentType): Boolean {
        if (contentType.match(ContentType.Application.Json)) {
            return true
        }

        val value = contentType.withoutParameters().toString()
        return value.startsWith("application/", ignoreCase = true) && value.endsWith("+json", ignoreCase = true)
    }
}
