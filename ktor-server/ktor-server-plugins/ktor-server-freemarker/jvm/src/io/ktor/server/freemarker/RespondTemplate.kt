/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.freemarker

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Responds with the specified [template] and data [model].
 *
 * @see FreeMarkerContent
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.freemarker.respondTemplate)
 */
public suspend fun ApplicationCall.respondTemplate(
    template: String,
    model: Any? = null,
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
): Unit = respond(FreeMarkerContent(template, model, etag, contentType))
