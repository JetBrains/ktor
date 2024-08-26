/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import libcurl.*

// These should have been CPointer<CURL> and CPointer<CURLM>, I suppose,
// but somehow cinterop tool makes them just opaque pointers.
@OptIn(ExperimentalForeignApi::class)
internal typealias EasyHandle = COpaquePointer

@OptIn(ExperimentalForeignApi::class)
internal typealias MultiHandle = COpaquePointer

@OptIn(ExperimentalForeignApi::class)
internal fun CURLMcode.verify() {
    if (this != CURLM_OK) {
        error("Unexpected curl verify: ${curl_multi_strerror(this)?.toKString()}")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun CURLcode.verify() {
    if (this != CURLE_OK) {
        error("Unexpected curl verify: ${curl_easy_strerror(this)?.toKString()}")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: Int) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: Long) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: CPointer<*>) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: CValuesRef<*>) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.option(option: CURLoption, optionValue: String) {
    curl_easy_setopt(this, option, optionValue).verify()
}

@OptIn(ExperimentalForeignApi::class)
internal fun EasyHandle.getInfo(info: CURLINFO, optionValue: CPointer<*>) {
    curl_easy_getinfo(this, info, optionValue).verify()
}

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal fun HttpRequestData.headersToCurl(): CPointer<curl_slist> {
    var result: CPointer<curl_slist>? = null

    mergeHeaders(headers, body) { key, value ->
        val header = "$key: $value"
        result = curl_slist_append(result, header)
    }

    result = curl_slist_append(result, "Expect:")
    return result!!
}

@OptIn(ExperimentalForeignApi::class)
internal fun UInt.fromCurl(): HttpProtocolVersion = when (this) {
    CURL_HTTP_VERSION_1_0 -> HttpProtocolVersion.HTTP_1_0
    CURL_HTTP_VERSION_1_1 -> HttpProtocolVersion.HTTP_1_1
    CURL_HTTP_VERSION_2_0 -> HttpProtocolVersion.HTTP_2_0
    /* old curl fallback */
    else -> HttpProtocolVersion.HTTP_1_1
}
