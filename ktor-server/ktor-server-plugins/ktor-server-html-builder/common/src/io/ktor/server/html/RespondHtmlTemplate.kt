/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.html

import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.html.*

/**
 * Responds to a client with an HTML response built based on a specified template.
 * You can learn more from [HTML DSL](https://ktor.io/docs/html-dsl.html).
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.html.respondHtmlTemplate)
 */
public suspend fun <TTemplate : Template<HTML>> ApplicationCall.respondHtmlTemplate(
    template: TTemplate,
    status: HttpStatusCode = HttpStatusCode.OK,
    body: TTemplate.() -> Unit
) {
    template.body()
    respondHtml(status) { with(template) { apply() } }
}
