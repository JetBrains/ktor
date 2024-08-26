/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*

internal actual fun platformRequestDefaultTransform(
    contentType: ContentType?,
    context: HttpRequestBuilder,
    body: Any
): OutgoingContent? = null

internal actual fun HttpClient.platformResponseDefaultTransformers() {}
