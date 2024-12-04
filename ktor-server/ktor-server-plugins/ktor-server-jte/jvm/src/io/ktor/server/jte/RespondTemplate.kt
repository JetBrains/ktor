/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jte

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Responds with the specified [template] passing [params].
 *
 * @see JteContent
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.jte.respondTemplate)
 */
public suspend fun ApplicationCall.respondTemplate(
    template: String,
    params: Map<String, Any?> = emptyMap(),
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
): Unit = respond(JteContent(template, params, etag, contentType))

/**
 * Responds with the specified [template] passing [params] as a vararg of [Pair].
 *
 * @see JteContent
 */
public suspend fun ApplicationCall.respondTemplate(
    template: String,
    vararg params: Pair<String, Any?>,
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8),
): Unit = respond(JteContent(template, params.toMap(), etag, contentType))
