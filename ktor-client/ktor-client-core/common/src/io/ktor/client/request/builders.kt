/*
* Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Executes an HTTP request using the provided [HttpRequestBuilder] configuration.
 *
 * This function sends a request with parameters specified in the [builder], allowing customization
 * of request settings such as URL, HTTP method, headers, and body.
 *
 * ## Usage Example
 * ```
 * val client = HttpClient()
 * val response = client.request {
 *     url("https://ktor.io")
 *     method = HttpMethod.Get
 * }
 * ```
 * This example demonstrates making a GET request to "https://ktor.io".
 *
 * ## Note
 * In addition to this generic [HttpClient.request] method, there are more specific extension functions, such as
 * [HttpClient.get], [HttpClient.post], [HttpClient.put], and [HttpClient.delete], which are often more
 * convenient for common HTTP methods.
 *
 * @param builder The [HttpRequestBuilder] used to configure request parameters. Defaults to an empty
 * builder if none is provided.
 * @return [HttpResponse] The response received from the server after executing the request.
 *
 * For more details, see [Making requests](https://ktor.io/docs/request.html).
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.request.request)
 */
public suspend inline fun HttpClient.request(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): HttpResponse = HttpStatement(builder, this).execute()

/**
 * Prepares an [HttpClient]'s request with the parameters specified using [builder].
 */
public suspend inline fun HttpClient.prepareRequest(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): HttpStatement = HttpStatement(builder, this)

/**
 * Executes an HTTP request using the provided [HttpRequestBuilder] configuration.
 *
 * This function sends a request with parameters specified in the [block], allowing customization
 * of request settings such as URL, HTTP method, headers, and body.
 *
 * ## Usage Example
 * ```
 * val client = HttpClient()
 * val response = client.request {
 *     url("https://ktor.io")
 *     method = HttpMethod.Get
 * }
 * ```
 * This example demonstrates making a GET request to "https://ktor.io".
 *
 * ## Note
 * In addition to this generic [HttpClient.request] method, there are more specific extension functions, such as
 * [HttpClient.get], [HttpClient.post], [HttpClient.put], and [HttpClient.delete], which are often more
 * convenient for common HTTP methods.
 *
 * @param block The [HttpRequestBuilder] block used to configure request parameters.
 * Defaults to an empty builder if none is provided.
 * @return [HttpResponse] The response received from the server after executing the request.
 *
 * For more details, see [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.request(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    request(HttpRequestBuilder().apply(block))

/**
 * Prepares an [HttpClient]'s request with the parameters specified using [block].
 */
public suspend inline fun HttpClient.prepareRequest(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareRequest(HttpRequestBuilder().apply(block))

/**
 * Executes an HTTP request using the provided [HttpRequestBuilder] configuration.
 *
 * This function sends a request to the [urlString] with parameters specified in the [block], allowing customization
 * of request settings such as URL, HTTP method, headers, and body.
 *
 * ## Usage Example
 * ```
 * val client = HttpClient()
 * val response = client.request("https://ktor.io") {
 *     method = HttpMethod.Get
 * }
 * ```
 * This example demonstrates making a GET request to "https://ktor.io".
 *
 * ## Note
 * In addition to this generic [HttpClient.request] method, there are more specific extension functions, such as
 * [HttpClient.get], [HttpClient.post], [HttpClient.put], and [HttpClient.delete], which are often more
 * convenient for common HTTP methods.
 *
 * @param [urlString] The URL to which the request is sent.
 * @param [block] The [HttpRequestBuilder] used to configure request parameters. Defaults to an empty
 * builder if none is provided.
 * @return [HttpResponse] The response received from the server after executing the request.
 *
 * For more details, see [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.request(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request {
    url(urlString)
    block()
}

/**
 * Prepares an [HttpClient]'s request with the [urlString] and the parameters configured in [block].
 */
public suspend inline fun HttpClient.prepareRequest(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareRequest {
    url(urlString)
    block()
}

/**
 * Executes an HTTP request using the provided [HttpRequestBuilder] configuration.
 *
 * This function sends a request to [url] with parameters specified in the [block], allowing customization
 * of request settings such as URL, HTTP method, headers, and body.
 *
 * ## Usage Example
 * ```
 * val client = HttpClient()
 * val response = client.request("https://ktor.io") {
 *     method = HttpMethod.Get
 * }
 * ```
 * This example demonstrates making a GET request to "https://ktor.io".
 *
 * ## Note
 * In addition to this generic [HttpClient.request] method, there are more specific extension functions, such as
 * [HttpClient.get], [HttpClient.post], [HttpClient.put], and [HttpClient.delete], which are often more
 * convenient for common HTTP methods.
 *
 * @param [url] The URL to which the request is sent.
 * @param [block] The [HttpRequestBuilder] used to configure request parameters. Defaults to an empty
 * builder if none is provided.
 * @return [HttpResponse] The response received from the server after executing the request.
 *
 * For more details, see [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.request(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request {
    url(url)
    block()
}

/**
 * Prepares an [HttpClient]'s request with the [url] and the parameters configured in [block].
 */
public suspend inline fun HttpClient.prepareRequest(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareRequest {
    url(url)
    block()
}

/**
 * Executes an [HttpClient]'s GET request with the parameters configured in [builder].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.get(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Get
    return request(builder)
}

/**
 * Executes an [HttpClient]'s POST request with the parameters configured in [builder].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.post(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Post
    return request(builder)
}

/**
 * Executes a [HttpClient] PUT request with the parameters configured in [builder].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.put(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Put
    return request(builder)
}

/**
 * Executes a [HttpClient] DELETE request with the parameters configured in [builder].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.delete(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Delete
    return request(builder)
}

/**
 * Executes a [HttpClient] OPTIONS request with the parameters configured in [builder].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.options(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Options
    return request(builder)
}

/**
 * Executes a [HttpClient] PATCH request with the parameters configured in [builder].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.patch(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Patch
    return request(builder)
}

/**
 * Executes a [HttpClient] HEAD request with the parameters configured in [builder].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.head(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Head
    return request(builder)
}

/**
 * Prepares an [HttpClient]'s GET request with the parameters configured in [builder].
 */
public suspend inline fun HttpClient.prepareGet(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Get
    return prepareRequest(builder)
}

/**
 * Prepares an [HttpClient]'s POST request with the parameters configured in [builder].
 */
public suspend inline fun HttpClient.preparePost(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Post
    return prepareRequest(builder)
}

/**
 * Prepares an [HttpClient]'s PUT request with the parameters configured in [builder].
 */
public suspend inline fun HttpClient.preparePut(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Put
    return prepareRequest(builder)
}

/**
 * Prepares an [HttpClient]'s DELETE request with the parameters configured in [builder].
 */
public suspend inline fun HttpClient.prepareDelete(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Delete
    return prepareRequest(builder)
}

/**
 * Prepares an [HttpClient]'s OPTIONS request with the parameters configured in [builder].
 */
public suspend inline fun HttpClient.prepareOptions(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Options
    return prepareRequest(builder)
}

/**
 * Prepares an [HttpClient]'s PATCH request with the parameters configured in [builder].
 */
public suspend inline fun HttpClient.preparePatch(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Patch
    return prepareRequest(builder)
}

/**
 * Prepares an [HttpClient]'s HEAD request with the parameters configured in [builder].
 */
public suspend inline fun HttpClient.prepareHead(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Head
    return prepareRequest(builder)
}

/**
 * Executes an [HttpClient]'s GET request with the parameters configured in [block].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.get(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    get(HttpRequestBuilder().apply(block))

/**
 * Executes an [HttpClient]'s POST request with the parameters configured in [block].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.post(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    post(HttpRequestBuilder().apply(block))

/**
 * Executes an [HttpClient]'s PUT request with the parameters configured in [block].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.put(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    put(HttpRequestBuilder().apply(block))

/**
 * Executes an [HttpClient]'s DELETE request with the parameters configured in [block].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.delete(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    delete(HttpRequestBuilder().apply(block))

/**
 * Executes an [HttpClient]'s OPTIONS request with the parameters configured in [block].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.options(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    options(HttpRequestBuilder().apply(block))

/**
 * Executes an [HttpClient]'s PATCH request with the parameters configured in [block].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.patch(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    patch(HttpRequestBuilder().apply(block))

/**
 * Executes an [HttpClient]'s HEAD request with the parameters configured in [block].
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.head(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    head(HttpRequestBuilder().apply(block))

/**
 * Prepares an [HttpClient]'s GET request with the parameters configured in [block].
 */
public suspend inline fun HttpClient.prepareGet(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareGet(HttpRequestBuilder().apply(block))

/**
 * Prepares an [HttpClient]'s POST request with the parameters configured in [block].
 */
public suspend inline fun HttpClient.preparePost(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePost(HttpRequestBuilder().apply(block))

/**
 * Prepares an [HttpClient]'s PUT request with the parameters configured in [block].
 */
public suspend inline fun HttpClient.preparePut(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePut(HttpRequestBuilder().apply(block))

/**
 * Prepares an [HttpClient]'s DELETE request with the parameters configured in [block].
 */
public suspend inline fun HttpClient.prepareDelete(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareDelete(HttpRequestBuilder().apply(block))

/**
 * Prepares an [HttpClient]'s OPTIONS request with the parameters configured in [block].
 */
public suspend inline fun HttpClient.prepareOptions(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareOptions(HttpRequestBuilder().apply(block))

/**
 * Prepares an [HttpClient]'s PATCH request with the parameters configured in [block].
 */
public suspend inline fun HttpClient.preparePatch(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePatch(HttpRequestBuilder().apply(block))

/**
 * Prepares an [HttpClient]'s HEAD request with the parameters configured in [block].
 */
public suspend inline fun HttpClient.prepareHead(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareHead(HttpRequestBuilder().apply(block))

/**
 * Creates an [HttpRequestBuilder] and configures it using [block].
 */
public fun request(block: HttpRequestBuilder.() -> Unit): HttpRequestBuilder =
    HttpRequestBuilder().apply(block)

/**
 * Executes an [HttpClient]'s GET request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.get(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = get { url(urlString); block() }

/**
 * Executes an [HttpClient]'s POST request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.post(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post { url(urlString); block() }

/**
 * Executes an [HttpClient]'s PUT request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.put(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = put { url(urlString); block() }

/**
 * Executes an [HttpClient]'s DELETE request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.delete(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = delete { url(urlString); block() }

/**
 * Executes an [HttpClient]'s OPTIONS request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.options(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = options { url(urlString); block() }

/**
 * Executes an [HttpClient]'s PATCH request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.patch(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = patch { url(urlString); block() }

/**
 * Executes an [HttpClient]'s HEAD request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 */
public suspend inline fun HttpClient.head(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = head { url(urlString); block() }

/**
 * Prepares an [HttpClient]'s GET request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 */
public suspend inline fun HttpClient.prepareGet(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareGet { url(urlString); block() }

/**
 * Prepares an [HttpClient]'s POST request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 */
public suspend inline fun HttpClient.preparePost(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePost { url(urlString); block() }

/**
 * Prepares an [HttpClient]'s PUT request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 */
public suspend inline fun HttpClient.preparePut(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePut { url(urlString); block() }

/**
 * Prepares an [HttpClient]'s DELETE request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 */
public suspend inline fun HttpClient.prepareDelete(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareDelete { url(urlString); block() }

/**
 * Prepares an [HttpClient]'s OPTIONS request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 */
public suspend inline fun HttpClient.prepareOptions(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareOptions { url(urlString); block() }

/**
 * Prepares an [HttpClient]'s PATCH request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 */
public suspend inline fun HttpClient.preparePatch(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePatch { url(urlString); block() }

/**
 * Prepares an [HttpClient]'s HEAD request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 */
public suspend inline fun HttpClient.prepareHead(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareHead { url(urlString); block() }
