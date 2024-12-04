/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

import io.ktor.http.*

/**
 * Builds an instance of [Headers] using the [block] function.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.utils.buildHeaders)
 */
public fun buildHeaders(block: HeadersBuilder.() -> Unit = {}): Headers =
    HeadersBuilder().apply(block).build()
