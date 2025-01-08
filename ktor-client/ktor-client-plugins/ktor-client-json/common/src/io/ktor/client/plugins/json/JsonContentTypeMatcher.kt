/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.json

import io.ktor.http.*

internal class JsonContentTypeMatcher : ContentTypeMatcher {
    override fun contains(contentType: ContentType): Boolean {
        if (ContentType.Application.Json.match(contentType)) {
            return true
        }

        val value = contentType.withoutParameters().toString()
        return value in ContentType.Application && value.endsWith("+json", ignoreCase = true)
    }
}
