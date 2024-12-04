/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Set `E-Tag` header
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.etag)
 */
public fun HeadersBuilder.etag(entityTag: String): Unit = set(HttpHeaders.ETag, entityTag)
