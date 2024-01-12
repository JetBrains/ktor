/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

internal val BodyTypeAttributeKey: AttributeKey<TypeInfo> = AttributeKey("BodyTypeAttributeKey")

@OptIn(InternalAPI::class)
public inline fun <reified T> HttpRequestBuilder.setBody(body: T) {
    when (body) {
        null -> {
            this.body = NullBody
            bodyType = typeInfo<T>()
        }
        is OutgoingContent -> {
            this.body = body
            bodyType = null
        }
        else -> {
            this.body = body
            bodyType = typeInfo<T>()
        }
    }
}

@OptIn(InternalAPI::class)
public fun HttpRequestBuilder.setBody(body: Any?, bodyType: TypeInfo) {
    this.body = body ?: NullBody
    this.bodyType = bodyType
}
