/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request.forms

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*

/**
 * Makes a request containing form parameters encoded using the `x-www-form-urlencoded` format.
 *
 * If [encodeInQuery] is set to `true`, form parameters are sent as URL parameters using the GET request.
 * Otherwise, form parameters are sent in a POST request body.
 *
 * Example: [Form parameters](https://ktor.io/docs/request.html#form_parameters).
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.request.forms.submitForm)
 */
public suspend inline fun HttpClient.submitForm(
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request {
    if (encodeInQuery) {
        method = HttpMethod.Get
        url.parameters.appendAll(formParameters)
    } else {
        method = HttpMethod.Post
        setBody(FormDataContent(formParameters))
    }

    block()
}

/**
 * Makes a request containing form parameters encoded using the `x-www-form-urlencoded` format.
 *
 * If [encodeInQuery] is set to `true`, form parameters are sent as URL parameters using the GET request.
 * Otherwise, form parameters are sent in a POST request body.
 *
 * Example: [Form parameters](https://ktor.io/docs/request.html#form_parameters).
 */
public suspend fun HttpClient.submitForm(
    url: String,
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = submitForm(formParameters, encodeInQuery) {
    url(url)
    block()
}

/**
 * Makes a POST request containing form parameters encoded using the `multipart/form-data` format.
 *
 * Example: [Upload a file](https://ktor.io/docs/request.html#upload_file).
 */
public suspend inline fun HttpClient.submitFormWithBinaryData(
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request {
    method = HttpMethod.Post
    setBody(MultiPartFormDataContent(formData))
    block()
}

/**
 * Makes a POST request containing form parameters encoded using the `multipart/form-data` format.
 *
 * Example: [Upload a file](https://ktor.io/docs/request.html#upload_file).
 */
public suspend inline fun HttpClient.submitFormWithBinaryData(
    url: String,
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = submitFormWithBinaryData(formData) {
    url(url)
    block()
}

/**
 * Prepares a request containing form parameters encoded using the `x-www-form-urlencoded` format.
 *
 * If [encodeInQuery] is set to `true`, form parameters are sent as URL parameters using the GET request.
 * Otherwise, form parameters are sent in a POST request body.
 */
public suspend inline fun HttpClient.prepareForm(
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareRequest {
    if (encodeInQuery) {
        method = HttpMethod.Get
        url.parameters.appendAll(formParameters)
    } else {
        method = HttpMethod.Post
        setBody(FormDataContent(formParameters))
    }

    block()
}

/**
 * Prepares a request containing form parameters encoded using the `x-www-form-urlencoded` format.
 *
 * If [encodeInQuery] is set to `true`, form parameters are sent as URL parameters using the GET request.
 * Otherwise, form parameters are sent in a POST request body.
 */
public suspend fun HttpClient.prepareForm(
    url: String,
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareForm(formParameters, encodeInQuery) {
    url(url)
    block()
}

/**
 * Prepares a POST request containing form parameters encoded using the `multipart/form-data` format.
 */
public suspend inline fun HttpClient.prepareFormWithBinaryData(
    formData: List<PartData>,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareRequest {
    method = HttpMethod.Post
    setBody(MultiPartFormDataContent(formData))
    block()
}

/**
 * Prepares a POST request containing form parameters encoded using the `multipart/form-data` format.
 */
public suspend inline fun HttpClient.prepareFormWithBinaryData(
    url: String,
    formData: List<PartData>,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareFormWithBinaryData(formData) {
    url(url)
    block()
}
