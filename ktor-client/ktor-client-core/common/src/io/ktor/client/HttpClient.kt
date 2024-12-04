/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.events.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * A multiplatform asynchronous HTTP client that allows you to make requests, handle responses,
 * and extend its functionality with plugins such as authentication, JSON serialization, and more.
 *
 * # Creating a client
 * To create a new client, you can call:
 * ```kotlin
 * val client = HttpClient()
 * ```
 * You can create as many clients as you need.
 *
 * If you no longer need the client, please consider closing it to release resources:
 * ```
 * client.close()
 * ```
 *
 * To learn more on how to create and configure an [HttpClient], see
 * [Creating and configuring a client](https://ktor.io/docs/create-client.html).
 *
 * # Making API Requests
 * For every HTTP method (GET, POST, PUT, etc.), there is a corresponding function:
 * ```kotlin
 * val response: HttpResponse = client.get("https://ktor.io/")
 * val body = response.bodyAsText()
 * ```
 * For more details, see [Making HTTP requests](https://ktor.io/docs/client-requests.html).
 *
 * # Query Parameters
 * Add query parameters to your request using the `parameter` function:
 * ```kotlin
 * val response = client.get("https://google.com/search") {
 *     url {
 *         parameter("q", "REST API with Ktor")
 *     }
 * }
 * ```
 * For more information, refer to [Passing request parameters](https://ktor.io/docs/client-requests.html#parameters).
 *
 * # Adding Headers
 * Include headers in your request using the `headers` builder or the `header` function:
 * ```kotlin
 * val response = client.get("https://httpbin.org/bearer") {
 *     headers {
 *         append("Authorization", "Bearer your_token_here")
 *         append("Accept", "application/json")
 *     }
 * }
 * ```
 * To learn more, see [Adding headers to a request](https://ktor.io/docs/client-requests.html#headers).
 *
 * # JSON Serialization
 * Add dependencies:
 * - io.ktor:ktor-client-content-negotiation:3.+
 * - io.ktor:ktor-serialization-kotlinx-json:3.+
 * Add Gradle plugin:
 * ```
 * plugins {
 *     kotlin("plugin.serialization")
 * }
 * ```
 *
 * Send and receive JSON data by installing the `ContentNegotiation` plugin with `kotlinx.serialization`:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 *
 * @Serializable
 * data class MyRequestType(val someData: String)
 *
 * @Serializable
 * data class MyResponseType(val someResponseData: String)
 *
 * val response: MyResponseType = client.post("https://api.example.com/data") {
 *     contentType(ContentType.Application.Json)
 *     setBody(MyRequestType(someData = "value"))
 * }.body()
 * ```
 * For Maven configuration and other details, see [Serializing JSON data](https://ktor.io/docs/client-serialization.html).
 *
 * # Submitting Forms
 * Submit form data using `FormDataContent` or the `submitForm` function:
 * ```kotlin
 * // Using FormDataContent
 * val response = client.post("https://example.com/submit") {
 *     setBody(FormDataContent(Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }))
 * }
 *
 * // Using submitForm
 * val response = client.submitForm(
 *     url = "https://example.com/submit",
 *     formParameters = Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }
 * )
 * ```
 * For more information, see [Submitting form parameters](https://ktor.io/docs/client-requests.html#form_parameters).
 *
 * # Handling Authentication
 * Add dependency: io.ktor:ktor-client-auth:3.+
 *
 * Use the `Auth` plugin to handle various authentication schemes like Basic or Bearer token authentication:
 * ```kotlin
 * val client = HttpClient {
 *     install(Auth) {
 *         bearer {
 *             loadTokens {
 *                 BearerTokens(accessToken = "your_access_token", refreshToken = "your_refresh_token")
 *             }
 *         }
 *     }
 * }
 *
 * val response = client.get("https://api.example.com/protected")
 * ```
 * For more details, refer to [Client authentication](https://ktor.io/docs/client-auth.html).
 *
 * # Setting Timeouts and Retries
 * Configure timeouts and implement retry logic for your requests:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpTimeout) {
 *         requestTimeoutMillis = 10000
 *         connectTimeoutMillis = 5000
 *         socketTimeoutMillis = 15000
 *     }
 * }
 * ```
 *
 * For the request timeout:
 * ```kotlin
 * client.get("") {
 *     timeout {
 *         requestTimeoutMillis = 1000
 *     }
 * }
 * ```
 * For more information, see the [Timeout](https://ktor.io/docs/client-timeout.html) documentation.
 *
 * # Handling Cookies
 *
 * Manage cookies automatically by installing the `HttpCookies` plugin:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpCookies) {
 *         storage = AcceptAllCookiesStorage()
 *     }
 * }
 *
 * // Accessing cookies
 * val cookies: List<Cookie> = client.cookies("https://example.com")
 * ```
 * For more information, see the [Cookies](https://ktor.io/docs/client-cookies.html) documentation.
 *
 * # Uploading Files
 * Upload files using multipart/form-data requests:
 * ```kotlin
 * client.submitFormWithBinaryData(
 *      url = "https://example.com/upload",
 *      formData = formData {
 *          append("description", "File upload example")
 *          append("file", {
 *              File("path/to/file.txt").readChannel()
 *          })
 *      }
 *  )
 *
 * For more details, see [Uploading data](https://ktor.io/docs/client-requests.html#upload_file).
 *
 * # Using WebSockets
 *
 * Communicate over WebSockets using the `webSocket` function:
 * ```kotlin
 * client.webSocket("wss://echo.websocket.org") {
 *     send(Frame.Text("Hello, WebSocket!"))
 *     val frame = incoming.receive()
 *     if (frame is Frame.Text) {
 *         println("Received: ${frame.readText()}")
 *     }
 * }
 * ```
 * To learn more, see [Client WebSockets](https://ktor.io/docs/client-websockets.html).
 *
 * # Error Handling
 * Handle exceptions and HTTP error responses gracefully:
 * val client = HttpClient {
 *     HttpResponseValidator {
 *         validateResponse { response ->
 *             val statusCode = response.status.value
 *             when (statusCode) {
 *                 in 300..399 -> error("Redirects are not allowed")
 *             }
 *         }
 *     }
 * }

 * For more information, see [Error handling](https://ktor.io/docs/client-response-validation.html).
 *
 * # Configuring SSL/TLS
 *
 * Customize SSL/TLS settings for secure connections is engine-specific. Please refer to the following page for
 * the details: [Client SSL/TLS](https://ktor.io/docs/client-ssl.html).
 *
 * # Using Proxies
 * Route requests through an HTTP or SOCKS proxy:
 * ```kotlin
 * val client = HttpClient() {
 *     engine {
 *         proxy = ProxyBuilder.http("http://proxy.example.com:8080")
 *         // For a SOCKS proxy:
 *         // proxy = ProxyBuilder.socks(host = "proxy.example.com", port = 1080)
 *     }
 * }
 * ```
 * For more details, see [Using a proxy](https://ktor.io/docs/client-proxy.html).
 *
 * # Streaming Data
 *
 * Stream large data efficiently without loading it entirely into memory.
 *
 * Stream request:
 * ```kotlin
 * val response = client.post("https://example.com/upload") {
 *      setBody(object: OutgoingContent.WriteChannelContent() {
 *          override suspend fun writeTo(channel: ByteWriteChannel) {
 *              repeat(1000) {
 *                  channel.writeString("Hello!")
 *              }
 *          }
 *      })
 * }
 * ```
 *
 * Stream response:
 * ```kotlin
 * client.prepareGet("https://example.com/largefile.zip").execute { response ->
 *     val channel: ByteReadChannel = response.bodyAsChannel()
 *
 *     while (!channel.exhausted()) {
 *         val chunk = channel.readBuffer()
 *         // ...
 *     }
 * }
 * ```
 * Learn more at [Streaming data](https://ktor.io/docs/client-responses.html#streaming).
 *
 * # Using SSE
 * Server-Sent Events (SSE) is a technology that allows a server to continuously push events to a client over an HTTP
 * connection. It's particularly useful in cases where the server needs to send event-based updates without requiring
 * the client to repeatedly poll the server.
 *
 * Install the plugin:
 * ```kotlin
 * val client = HttpClient(CIO) {
 *     install(SSE)
 * }
 * ```
 *
 * ```
 * client.sse(host = "0.0.0.0", port = 8080, path = "/events") {
 *     while (true) {
 *         for (event in incoming) {
 *             println("Event from server:")
 *             println(event)
 *         }
 *     }
 * }
 * ```
 *
 * For more information, see [Using SSE](https://ktor.io/docs/client-server-sent-events.html#install_plugin).
 *
 * # Customizing a client with plugins
 * To extend out-of-the-box functionality, you can install plugins for a Ktor client:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 * ```
 *
 * There are many plugins available out of the box, and you can write your own. See
 * [Create custom client plugins](https://ktor.io/docs/client-custom-plugins.html) to learn more.
 *
 * # Service Loader and Default Engine
 * On JVM, calling `HttpClient()` without specifying an engine uses a service loader mechanism to
 * determine the appropriate default engine. This can introduce a performance overhead, especially on
 * slower devices, such as Android.
 *
 * **Performance Note**: If you are targeting platforms where initialization speed is critical,
 * consider explicitly specifying an engine to avoid the service loader lookup.
 *
 * Example with manual engine specification:
 * ```
 * val client = HttpClient(Apache) // Explicitly uses Apache engine, bypassing service loader
 * ```
 *
 * By directly setting the engine (e.g., `Apache`, `OkHttp`), you can optimize startup performance
 * by preventing the default service loader mechanism.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.HttpClient)
 */
@KtorDsl
public expect fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit = {}
): HttpClient

/**
 * A multiplatform asynchronous HTTP client that allows you to make requests, handle responses,
 * and extend its functionality with plugins such as authentication, JSON serialization, and more.
 *
 * # Creating a client
 * To create a new client, you can call:
 * ```kotlin
 * val client = HttpClient()
 * ```
 * You can create as many clients as you need.
 *
 * If you no longer need the client, please consider closing it to release resources:
 * ```
 * client.close()
 * ```
 *
 * To learn more on how to create and configure an [HttpClient], see
 * [Creating and configuring a client](https://ktor.io/docs/create-client.html).
 *
 * # Making API Requests
 * For every HTTP method (GET, POST, PUT, etc.), there is a corresponding function:
 * ```kotlin
 * val response: HttpResponse = client.get("https://ktor.io/")
 * val body = response.bodyAsText()
 * ```
 * For more details, see [Making HTTP requests](https://ktor.io/docs/client-requests.html).
 *
 * # Query Parameters
 * Add query parameters to your request using the `parameter` function:
 * ```kotlin
 * val response = client.get("https://google.com/search") {
 *     url {
 *         parameter("q", "REST API with Ktor")
 *     }
 * }
 * ```
 * For more information, refer to [Passing request parameters](https://ktor.io/docs/client-requests.html#parameters).
 *
 * # Adding Headers
 * Include headers in your request using the `headers` builder or the `header` function:
 * ```kotlin
 * val response = client.get("https://httpbin.org/bearer") {
 *     headers {
 *         append("Authorization", "Bearer your_token_here")
 *         append("Accept", "application/json")
 *     }
 * }
 * ```
 * To learn more, see [Adding headers to a request](https://ktor.io/docs/client-requests.html#headers).
 *
 * # JSON Serialization
 * Add dependencies:
 * - io.ktor:ktor-client-content-negotiation:3.+
 * - io.ktor:ktor-serialization-kotlinx-json:3.+
 * Add Gradle plugin:
 * ```
 * plugins {
 *     kotlin("plugin.serialization")
 * }
 * ```
 *
 * Send and receive JSON data by installing the `ContentNegotiation` plugin with `kotlinx.serialization`:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 *
 * @Serializable
 * data class MyRequestType(val someData: String)
 *
 * @Serializable
 * data class MyResponseType(val someResponseData: String)
 *
 * val response: MyResponseType = client.post("https://api.example.com/data") {
 *     contentType(ContentType.Application.Json)
 *     setBody(MyRequestType(someData = "value"))
 * }.body()
 * ```
 * For Maven configuration and other details, see [Serializing JSON data](https://ktor.io/docs/client-serialization.html).
 *
 * # Submitting Forms
 * Submit form data using `FormDataContent` or the `submitForm` function:
 * ```kotlin
 * // Using FormDataContent
 * val response = client.post("https://example.com/submit") {
 *     setBody(FormDataContent(Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }))
 * }
 *
 * // Using submitForm
 * val response = client.submitForm(
 *     url = "https://example.com/submit",
 *     formParameters = Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }
 * )
 * ```
 * For more information, see [Submitting form parameters](https://ktor.io/docs/client-requests.html#form_parameters).
 *
 * # Handling Authentication
 * Add dependency: io.ktor:ktor-client-auth:3.+
 *
 * Use the `Auth` plugin to handle various authentication schemes like Basic or Bearer token authentication:
 * ```kotlin
 * val client = HttpClient {
 *     install(Auth) {
 *         bearer {
 *             loadTokens {
 *                 BearerTokens(accessToken = "your_access_token", refreshToken = "your_refresh_token")
 *             }
 *         }
 *     }
 * }
 *
 * val response = client.get("https://api.example.com/protected")
 * ```
 * For more details, refer to [Client authentication](https://ktor.io/docs/client-auth.html).
 *
 * # Setting Timeouts and Retries
 * Configure timeouts and implement retry logic for your requests:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpTimeout) {
 *         requestTimeoutMillis = 10000
 *         connectTimeoutMillis = 5000
 *         socketTimeoutMillis = 15000
 *     }
 * }
 * ```
 *
 * For the request timeout:
 * ```kotlin
 * client.get("") {
 *     timeout {
 *         requestTimeoutMillis = 1000
 *     }
 * }
 * ```
 * For more information, see the [Timeout](https://ktor.io/docs/client-timeout.html) documentation.
 *
 * # Handling Cookies
 *
 * Manage cookies automatically by installing the `HttpCookies` plugin:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpCookies) {
 *         storage = AcceptAllCookiesStorage()
 *     }
 * }
 *
 * // Accessing cookies
 * val cookies: List<Cookie> = client.cookies("https://example.com")
 * ```
 * For more information, see the [Cookies](https://ktor.io/docs/client-cookies.html) documentation.
 *
 * # Uploading Files
 * Upload files using multipart/form-data requests:
 * ```kotlin
 * client.submitFormWithBinaryData(
 *      url = "https://example.com/upload",
 *      formData = formData {
 *          append("description", "File upload example")
 *          append("file", {
 *              File("path/to/file.txt").readChannel()
 *          })
 *      }
 *  )
 *
 * For more details, see [Uploading data](https://ktor.io/docs/client-requests.html#upload_file).
 *
 * # Using WebSockets
 *
 * Communicate over WebSockets using the `webSocket` function:
 * ```kotlin
 * client.webSocket("wss://echo.websocket.org") {
 *     send(Frame.Text("Hello, WebSocket!"))
 *     val frame = incoming.receive()
 *     if (frame is Frame.Text) {
 *         println("Received: ${frame.readText()}")
 *     }
 * }
 * ```
 * To learn more, see [Client WebSockets](https://ktor.io/docs/client-websockets.html).
 *
 * # Error Handling
 * Handle exceptions and HTTP error responses gracefully:
 * val client = HttpClient {
 *     HttpResponseValidator {
 *         validateResponse { response ->
 *             val statusCode = response.status.value
 *             when (statusCode) {
 *                 in 300..399 -> error("Redirects are not allowed")
 *             }
 *         }
 *     }
 * }

 * For more information, see [Error handling](https://ktor.io/docs/client-response-validation.html).
 *
 * # Configuring SSL/TLS
 *
 * Customize SSL/TLS settings for secure connections is engine-specific. Please refer to the following page for
 * the details: [Client SSL/TLS](https://ktor.io/docs/client-ssl.html).
 *
 * # Using Proxies
 * Route requests through an HTTP or SOCKS proxy:
 * ```kotlin
 * val client = HttpClient() {
 *     engine {
 *         proxy = ProxyBuilder.http("http://proxy.example.com:8080")
 *         // For a SOCKS proxy:
 *         // proxy = ProxyBuilder.socks(host = "proxy.example.com", port = 1080)
 *     }
 * }
 * ```
 * For more details, see [Using a proxy](https://ktor.io/docs/client-proxy.html).
 *
 * # Streaming Data
 *
 * Stream large data efficiently without loading it entirely into memory.
 *
 * Stream request:
 * ```kotlin
 * val response = client.post("https://example.com/upload") {
 *      setBody(object: OutgoingContent.WriteChannelContent() {
 *          override suspend fun writeTo(channel: ByteWriteChannel) {
 *              repeat(1000) {
 *                  channel.writeString("Hello!")
 *              }
 *          }
 *      })
 * }
 * ```
 *
 * Stream response:
 * ```kotlin
 * client.prepareGet("https://example.com/largefile.zip").execute { response ->
 *     val channel: ByteReadChannel = response.bodyAsChannel()
 *
 *     while (!channel.exhausted()) {
 *         val chunk = channel.readBuffer()
 *         // ...
 *     }
 * }
 * ```
 * Learn more at [Streaming data](https://ktor.io/docs/client-responses.html#streaming).
 *
 * # Using SSE
 * Server-Sent Events (SSE) is a technology that allows a server to continuously push events to a client over an HTTP
 * connection. It's particularly useful in cases where the server needs to send event-based updates without requiring
 * the client to repeatedly poll the server.
 *
 * Install the plugin:
 * ```kotlin
 * val client = HttpClient(CIO) {
 *     install(SSE)
 * }
 * ```
 *
 * ```
 * client.sse(host = "0.0.0.0", port = 8080, path = "/events") {
 *     while (true) {
 *         for (event in incoming) {
 *             println("Event from server:")
 *             println(event)
 *         }
 *     }
 * }
 * ```
 *
 * For more information, see [Using SSE](https://ktor.io/docs/client-server-sent-events.html#install_plugin).
 *
 * # Customizing a client with plugins
 * To extend out-of-the-box functionality, you can install plugins for a Ktor client:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 * ```
 *
 * There are many plugins available out of the box, and you can write your own. See
 * [Create custom client plugins](https://ktor.io/docs/client-custom-plugins.html) to learn more.
 *
 * # Service Loader and Default Engine
 * On JVM, calling `HttpClient()` without specifying an engine uses a service loader mechanism to
 * determine the appropriate default engine. This can introduce a performance overhead, especially on
 * slower devices, such as Android.
 *
 * **Performance Note**: If you are targeting platforms where initialization speed is critical,
 * consider explicitly specifying an engine to avoid the service loader lookup.
 *
 * Example with manual engine specification:
 * ```
 * val client = HttpClient(Apache) // Explicitly uses Apache engine, bypassing service loader
 * ```
 *
 * By directly setting the engine (e.g., `Apache`, `OkHttp`), you can optimize startup performance
 * by preventing the default service loader mechanism.
 */
@KtorDsl
public fun <T : HttpClientEngineConfig> HttpClient(
    engineFactory: HttpClientEngineFactory<T>,
    block: HttpClientConfig<T>.() -> Unit = {}
): HttpClient {
    val config: HttpClientConfig<T> = HttpClientConfig<T>().apply(block)
    val engine = engineFactory.create(config.engineConfig)
    val client = HttpClient(engine, config, manageEngine = true)

    // If the engine was created using factory Ktor is responsible for its lifecycle management. Otherwise user has to
    // close engine by themself.
    client.coroutineContext[Job]!!.invokeOnCompletion {
        engine.close()
    }

    return client
}

/**
 * A multiplatform asynchronous HTTP client that allows you to make requests, handle responses,
 * and extend its functionality with plugins such as authentication, JSON serialization, and more.
 *
 * # Creating a client
 * To create a new client, you can call:
 * ```kotlin
 * val client = HttpClient()
 * ```
 * You can create as many clients as you need.
 *
 * If you no longer need the client, please consider closing it to release resources:
 * ```
 * client.close()
 * ```
 *
 * To learn more on how to create and configure an [HttpClient] see the tutorial page:
 * [Creating and configuring a client](https://ktor.io/docs/create-client.html).
 *
 * # Making API Requests
 * For every HTTP method (GET, POST, PUT, etc.), there is a corresponding function:
 * ```kotlin
 * val response: HttpResponse = client.get("https://ktor.io/")
 * val body = response.bodyAsText()
 * ```
 * See [Making HTTP requests](https://ktor.io/docs/client-requests.html) for more details.
 *
 * # Query Parameters
 * Add query parameters to your request using the `parameter` function:
 * ```kotlin
 * val response = client.get("https://google.com/search") {
 *     url {
 *         parameter("q", "REST API with Ktor")
 *     }
 * }
 * ```
 * For more information, refer to [Passing request parameters](https://ktor.io/docs/client-requests.html#parameters).
 *
 * # Adding Headers
 * Include headers in your request using the `headers` builder or the `header` function:
 * ```kotlin
 * val response = client.get("https://httpbin.org/bearer") {
 *     headers {
 *         append("Authorization", "Bearer your_token_here")
 *         append("Accept", "application/json")
 *     }
 * }
 * ```
 * Learn more at [Adding headers to a request](https://ktor.io/docs/client-requests.html#headers).
 *
 * # JSON Serialization
 * Add dependencies:
 * - io.ktor:ktor-client-content-negotiation:3.+
 * - io.ktor:ktor-serialization-kotlinx-json:3.+
 * Add Gradle plugin:
 * ```
 * plugins {
 *     kotlin("plugin.serialization")
 * }
 * ```
 *
 * Send and receive JSON data by installing the `ContentNegotiation` plugin with `kotlinx.serialization`:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 *
 * @Serializable
 * data class MyRequestType(val someData: String)
 *
 * @Serializable
 * data class MyResponseType(val someResponseData: String)
 *
 * val response: MyResponseType = client.post("https://api.example.com/data") {
 *     contentType(ContentType.Application.Json)
 *     setBody(MyRequestType(someData = "value"))
 * }.body()
 * ```
 * See [Serializing JSON data](https://ktor.io/docs/client-serialization.html) for maven configuration and other details.
 *
 * # Submitting Forms
 * Submit form data using `FormDataContent` or the `submitForm` function:
 * ```kotlin
 * // Using FormDataContent
 * val response = client.post("https://example.com/submit") {
 *     setBody(FormDataContent(Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }))
 * }
 *
 * // Or using submitForm
 * val response = client.submitForm(
 *     url = "https://example.com/submit",
 *     formParameters = Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }
 * )
 * ```
 * More information is available at [Submitting form parameters](https://ktor.io/docs/client-requests.html#form_parameters).
 *
 * # Handling Authentication
 * Add dependency: io.ktor:ktor-client-auth:3.+
 *
 * Use the `Auth` plugin to handle various authentication schemes like Basic or Bearer token authentication:
 * ```kotlin
 * val client = HttpClient {
 *     install(Auth) {
 *         bearer {
 *             loadTokens {
 *                 BearerTokens(accessToken = "your_access_token", refreshToken = "your_refresh_token")
 *             }
 *         }
 *     }
 * }
 *
 * val response = client.get("https://api.example.com/protected")
 * ```
 * Refer to [Client authentication](https://ktor.io/docs/client-auth.html) for more details.
 *
 * # Setting Timeouts and Retries
 * Configure timeouts and implement retry logic for your requests:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpTimeout) {
 *         requestTimeoutMillis = 10000
 *         connectTimeoutMillis = 5000
 *         socketTimeoutMillis = 15000
 *     }
 * }
 * ```
 *
 * For the request timeout:
 * ```kotlin
 * client.get("") {
 *     timeout {
 *         requestTimeoutMillis = 1000
 *     }
 * }
 * ```
 * See [Timeout](https://ktor.io/docs/client-timeout.html) for more information.
 *
 * # Handling Cookies
 *
 * Manage cookies automatically by installing the `HttpCookies` plugin:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpCookies) {
 *         storage = AcceptAllCookiesStorage()
 *     }
 * }
 *
 * // Accessing cookies
 * val cookies: List<Cookie> = client.cookies("https://example.com")
 * ```
 * Learn more at [Cookies](https://ktor.io/docs/client-cookies.html).
 *
 * # Uploading Files
 * Upload files using multipart/form-data requests:
 * ```kotlin
 * client.submitFormWithBinaryData(
 *      url = "https://example.com/upload",
 *      formData = formData {
 *          append("description", "File upload example")
 *          append("file", {
 *              File("path/to/file.txt").readChannel()
 *          })
 *      }
 *  )
 *
 * See [Uploading data](https://ktor.io/docs/client-requests.html#upload_file) for details.
 *
 * # Using WebSockets
 *
 * Communicate over WebSockets using the `webSocket` function:
 * ```kotlin
 * client.webSocket("wss://echo.websocket.org") {
 *     send(Frame.Text("Hello, WebSocket!"))
 *     val frame = incoming.receive()
 *     if (frame is Frame.Text) {
 *         println("Received: ${frame.readText()}")
 *     }
 * }
 * ```
 * Learn more at [Client WebSockets](https://ktor.io/docs/client-websockets.html).
 *
 * # Error Handling
 * Handle exceptions and HTTP error responses gracefully:
 * val client = HttpClient {
 *     HttpResponseValidator {
 *         validateResponse { response ->
 *             val statusCode = response.status.value
 *             when (statusCode) {
 *                 in 300..399 -> error("Redirects are not allowed")
 *             }
 *         }
 *     }
 * }

 * See [Error handling](https://ktor.io/docs/client-response-validation.html) for more information.
 *
 * # Configuring SSL/TLS
 *
 * Customize SSL/TLS settings for secure connections is engine-specific. Please refer to the following page for
 * the details: [Client SSL/TLS](https://ktor.io/docs/client-ssl.html).
 *
 * # Using Proxies
 * Route requests through an HTTP or SOCKS proxy:
 * ```kotlin
 * val client = HttpClient() {
 *     engine {
 *         proxy = ProxyBuilder.http("http://proxy.example.com:8080")
 *         // For a SOCKS proxy:
 *         // proxy = ProxyBuilder.socks(host = "proxy.example.com", port = 1080)
 *     }
 * }
 * ```
 * See [Using a proxy](https://ktor.io/docs/client-proxy.html) for details.
 *
 * # Streaming Data
 *
 * Stream large data efficiently without loading it entirely into memory.
 *
 * Stream request:
 * ```kotlin
 * val response = client.post("https://example.com/upload") {
 *      setBody(object: OutgoingContent.WriteChannelContent() {
 *          override suspend fun writeTo(channel: ByteWriteChannel) {
 *              repeat(1000) {
 *                  channel.writeString("Hello!")
 *              }
 *          }
 *      })
 * }
 * ```
 *
 * Stream response:
 * ```kotlin
 * client.prepareGet("https://example.com/largefile.zip").execute { response ->
 *     val channel: ByteReadChannel = response.bodyAsChannel()
 *
 *     while (!channel.exhausted()) {
 *         val chunk = channel.readBuffer()
 *         // ...
 *     }
 * }
 * ```
 * Learn more at [Streaming data](https://ktor.io/docs/client-responses.html#streaming).
 *
 * # Using SSE
 * Server-Sent Events (SSE) is a technology that allows a server to continuously push events to a client over an HTTP
 * connection. It's particularly useful in cases where the server needs to send event-based updates without requiring
 * the client to repeatedly poll the server.
 *
 * Install the plugin:
 * ```kotlin
 * val client = HttpClient(CIO) {
 *     install(SSE)
 * }
 * ```
 *
 * ```
 * client.sse(host = "0.0.0.0", port = 8080, path = "/events") {
 *     while (true) {
 *         for (event in incoming) {
 *             println("Event from server:")
 *             println(event)
 *         }
 *     }
 * }
 * ```
 *
 * Visit [Using SSE](https://ktor.io/docs/client-server-sent-events.html#install_plugin) to learn more.
 *
 * # Customizing a client with plugins
 * To extend out-of-the-box functionality, you can install plugins for a Ktor client:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 * ```
 *
 * There are many plugins available out of the box, and you can write your own. See
 * [Create custom client plugins](https://ktor.io/docs/client-custom-plugins.html) to learn more.
 *
 * # Service Loader and Default Engine
 * On JVM, calling `HttpClient()` without specifying an engine uses a service loader mechanism to
 * determine the appropriate default engine. This can introduce a performance overhead, especially on
 * slower devices, such as Android.
 *
 * **Performance Note**: If you are targeting platforms where initialization speed is critical,
 * consider explicitly specifying an engine to avoid the service loader lookup.
 *
 * Example with manual engine specification:
 * ```
 * val client = HttpClient(Apache) // Explicitly uses Apache engine, bypassing service loader
 * ```
 *
 * By directly setting the engine (e.g., `Apache`, `OkHttp`), you can optimize startup performance
 * by preventing the default service loader mechanism.
 */
@KtorDsl
public fun HttpClient(
    engine: HttpClientEngine,
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(engine, HttpClientConfig<HttpClientEngineConfig>().apply(block), manageEngine = false)

/**
 * A multiplatform asynchronous HTTP client that allows you to make requests, handle responses,
 * and extend its functionality with plugins such as authentication, JSON serialization, and more.
 *
 * # Creating client
 * To create a new client, you can call:
 * ```kotlin
 * val client = HttpClient()
 * ```
 * You can create as many clients as you need.
 *
 * If you no longer need the client, please consider closing it to release resources:
 * ```
 * client.close()
 * ```
 *
 * To learn more on how to create and configure an [HttpClient] see the tutorial page:
 * [Creating and configuring a client](https://ktor.io/docs/create-client.html).
 *
 * # Making API Requests
 * For every HTTP method (GET, POST, PUT, etc.), there is a corresponding function:
 * ```kotlin
 * val response: HttpResponse = client.get("https://ktor.io/")
 * val body = response.bodyAsText()
 * ```
 * See [Making HTTP requests](https://ktor.io/docs/client-requests.html) for more details.
 *
 * # Query Parameters
 * Add query parameters to your request using the `parameter` function:
 * ```kotlin
 * val response = client.get("https://google.com/search") {
 *     url {
 *         parameter("q", "REST API with Ktor")
 *     }
 * }
 * ```
 * For more information, refer to [Passing request parameters](https://ktor.io/docs/client-requests.html#parameters).
 *
 * # Adding Headers
 * Include headers in your request using the `headers` builder or the `header` function:
 * ```kotlin
 * val response = client.get("https://httpbin.org/bearer") {
 *     headers {
 *         append("Authorization", "Bearer your_token_here")
 *         append("Accept", "application/json")
 *     }
 * }
 * ```
 * Learn more at [Adding headers to a request](https://ktor.io/docs/client-requests.html#headers).
 *
 * # JSON Serialization
 * Add dependencies:
 * - io.ktor:ktor-client-content-negotiation:3.+
 * - io.ktor:ktor-serialization-kotlinx-json:3.+
 * Add Gradle plugin:
 * ```
 * plugins {
 *     kotlin("plugin.serialization")
 * }
 * ```
 *
 * Send and receive JSON data by installing the `ContentNegotiation` plugin with `kotlinx.serialization`:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 *
 * @Serializable
 * data class MyRequestType(val someData: String)
 *
 * @Serializable
 * data class MyResponseType(val someResponseData: String)
 *
 * val response: MyResponseType = client.post("https://api.example.com/data") {
 *     contentType(ContentType.Application.Json)
 *     setBody(MyRequestType(someData = "value"))
 * }.body()
 * ```
 * See [Serializing JSON data](https://ktor.io/docs/client-serialization.html) for maven configuration and other details.
 *
 * # Submitting Forms
 * Submit form data using `FormDataContent` or the `submitForm` function:
 * ```kotlin
 * // Using FormDataContent
 * val response = client.post("https://example.com/submit") {
 *     setBody(FormDataContent(Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }))
 * }
 *
 * // Or using submitForm
 * val response = client.submitForm(
 *     url = "https://example.com/submit",
 *     formParameters = Parameters.build {
 *         append("username", "user")
 *         append("password", "pass")
 *     }
 * )
 * ```
 * More information is available at [Submitting form parameters](https://ktor.io/docs/client-requests.html#form_parameters).
 *
 * # Handling Authentication
 * Add dependency: io.ktor:ktor-client-auth:3.+
 *
 * Use the `Auth` plugin to handle various authentication schemes like Basic or Bearer token authentication:
 * ```kotlin
 * val client = HttpClient {
 *     install(Auth) {
 *         bearer {
 *             loadTokens {
 *                 BearerTokens(accessToken = "your_access_token", refreshToken = "your_refresh_token")
 *             }
 *         }
 *     }
 * }
 *
 * val response = client.get("https://api.example.com/protected")
 * ```
 * Refer to [Client authentication](https://ktor.io/docs/client-auth.html) for more details.
 *
 * # Setting Timeouts and Retries
 * Configure timeouts and implement retry logic for your requests:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpTimeout) {
 *         requestTimeoutMillis = 10000
 *         connectTimeoutMillis = 5000
 *         socketTimeoutMillis = 15000
 *     }
 * }
 * ```
 *
 * For the request timeout:
 * ```kotlin
 * client.get("") {
 *     timeout {
 *         requestTimeoutMillis = 1000
 *     }
 * }
 * ```
 * See [Timeout](https://ktor.io/docs/client-timeout.html) for more information.
 *
 * # Handling Cookies
 *
 * Manage cookies automatically by installing the `HttpCookies` plugin:
 * ```kotlin
 * val client = HttpClient {
 *     install(HttpCookies) {
 *         storage = AcceptAllCookiesStorage()
 *     }
 * }
 *
 * // Accessing cookies
 * val cookies: List<Cookie> = client.cookies("https://example.com")
 * ```
 * Learn more at [Cookies](https://ktor.io/docs/client-cookies.html).
 *
 * # Uploading Files
 * Upload files using multipart/form-data requests:
 * ```kotlin
 * client.submitFormWithBinaryData(
 *      url = "https://example.com/upload",
 *      formData = formData {
 *          append("description", "File upload example")
 *          append("file", {
 *              File("path/to/file.txt").readChannel()
 *          })
 *      }
 *  )
 *
 * See [Uploading data](https://ktor.io/docs/client-requests.html#upload_file) for details.
 *
 * # Using WebSockets
 *
 * Communicate over WebSockets using the `webSocket` function:
 * ```kotlin
 * client.webSocket("wss://echo.websocket.org") {
 *     send(Frame.Text("Hello, WebSocket!"))
 *     val frame = incoming.receive()
 *     if (frame is Frame.Text) {
 *         println("Received: ${frame.readText()}")
 *     }
 * }
 * ```
 * Learn more at [Client WebSockets](https://ktor.io/docs/client-websockets.html).
 *
 * # Error Handling
 * Handle exceptions and HTTP error responses gracefully:
 * val client = HttpClient {
 *     HttpResponseValidator {
 *         validateResponse { response ->
 *             val statusCode = response.status.value
 *             when (statusCode) {
 *                 in 300..399 -> error("Redirects are not allowed")
 *             }
 *         }
 *     }
 * }

 * See [Error handling](https://ktor.io/docs/client-response-validation.html) for more information.
 *
 * # Configuring SSL/TLS
 *
 * Customize SSL/TLS settings for secure connections is engine-specific. Please refer to the following page for
 * the details: [Client SSL/TLS](https://ktor.io/docs/client-ssl.html).
 *
 * # Using Proxies
 * Route requests through an HTTP or SOCKS proxy:
 * ```kotlin
 * val client = HttpClient() {
 *     engine {
 *         proxy = ProxyBuilder.http("http://proxy.example.com:8080")
 *         // For a SOCKS proxy:
 *         // proxy = ProxyBuilder.socks(host = "proxy.example.com", port = 1080)
 *     }
 * }
 * ```
 * See [Using a proxy](https://ktor.io/docs/client-proxy.html) for details.
 *
 * # Streaming Data
 *
 * Stream large data efficiently without loading it entirely into memory.
 *
 * Stream request:
 * ```kotlin
 * val response = client.post("https://example.com/upload") {
 *      setBody(object: OutgoingContent.WriteChannelContent() {
 *          override suspend fun writeTo(channel: ByteWriteChannel) {
 *              repeat(1000) {
 *                  channel.writeString("Hello!")
 *              }
 *          }
 *      })
 * }
 * ```
 *
 * Stream response:
 * ```kotlin
 * client.prepareGet("https://example.com/largefile.zip").execute { response ->
 *     val channel: ByteReadChannel = response.bodyAsChannel()
 *
 *     while (!channel.exhausted()) {
 *         val chunk = channel.readBuffer()
 *         // ...
 *     }
 * }
 * ```
 * Learn more at [Streaming data](https://ktor.io/docs/client-responses.html#streaming).
 *
 * # Using SSE
 * Server-Sent Events (SSE) is a technology that allows a server to continuously push events to a client over an HTTP
 * connection. It's particularly useful in cases where the server needs to send event-based updates without requiring
 * the client to repeatedly poll the server.
 *
 * Install the plugin:
 * ```kotlin
 * val client = HttpClient(CIO) {
 *     install(SSE)
 * }
 * ```
 *
 * ```
 * client.sse(host = "0.0.0.0", port = 8080, path = "/events") {
 *     while (true) {
 *         for (event in incoming) {
 *             println("Event from server:")
 *             println(event)
 *         }
 *     }
 * }
 * ```
 *
 * Visit [Using SSE](https://ktor.io/docs/client-server-sent-events.html#install_plugin) to learn more.
 *
 * # Customizing a client with plugins
 * To extend out-of-the-box functionality, you can install plugins for a Ktor client:
 * ```kotlin
 * val client = HttpClient {
 *     install(ContentNegotiation) {
 *         json()
 *     }
 * }
 * ```
 *
 * There are many plugins available out of the box, and you can write your own. See
 * [Create custom client plugins](https://ktor.io/docs/client-custom-plugins.html) to learn more.
 *
 * # Service Loader and Default Engine
 * On JVM, calling `HttpClient()` without specifying an engine uses a service loader mechanism to
 * determine the appropriate default engine. This can introduce a performance overhead, especially on
 * slower devices, such as Android.
 *
 * **Performance Note**: If you are targeting platforms where initialization speed is critical,
 * consider explicitly specifying an engine to avoid the service loader lookup.
 *
 * Example with manual engine specification:
 * ```
 * val client = HttpClient(Apache) // Explicitly uses Apache engine, bypassing service loader
 * ```
 *
 * By directly setting the engine (e.g., `Apache`, `OkHttp`), you can optimize startup performance
 * by preventing the default service loader mechanism.
 */
@OptIn(InternalAPI::class)
public class HttpClient(
    public val engine: HttpClientEngine,
    private val userConfig: HttpClientConfig<out HttpClientEngineConfig> = HttpClientConfig()
) : CoroutineScope, Closeable {
    private var manageEngine: Boolean = false

    internal constructor(
        engine: HttpClientEngine,
        userConfig: HttpClientConfig<out HttpClientEngineConfig>,
        manageEngine: Boolean
    ) : this(engine, userConfig) {
        this.manageEngine = manageEngine
    }

    private val closed = atomic(false)

    private val clientJob: CompletableJob = Job(engine.coroutineContext[Job])

    public override val coroutineContext: CoroutineContext = engine.coroutineContext + clientJob

    /**
     * A pipeline used for processing all requests sent by this client.
     */
    public val requestPipeline: HttpRequestPipeline = HttpRequestPipeline()

    /**
     * A pipeline used for processing all responses sent by the server.
     */
    public val responsePipeline: HttpResponsePipeline = HttpResponsePipeline()

    /**
     * A pipeline used for sending a request.
     */
    public val sendPipeline: HttpSendPipeline = HttpSendPipeline()

    /**
     * A pipeline used for receiving a request.
     */
    public val receivePipeline: HttpReceivePipeline = HttpReceivePipeline()

    /**
     * Typed attributes used as a lightweight container for this client.
     */
    public val attributes: Attributes = Attributes(concurrent = true)

    /**
     * Provides access to the client's engine configuration.
     */
    public val engineConfig: HttpClientEngineConfig = engine.config

    /**
     * Provides access to the events of the client's lifecycle.
     */
    public val monitor: Events = Events()

    internal val config = HttpClientConfig<HttpClientEngineConfig>()

    init {
        if (manageEngine) {
            clientJob.invokeOnCompletion {
                if (it != null) {
                    engine.cancel()
                }
            }
        }

        engine.install(this)

        sendPipeline.intercept(HttpSendPipeline.Receive) { call ->
            check(call is HttpClientCall) { "Error: HttpClientCall expected, but found $call(${call::class})." }
            val response = receivePipeline.execute(Unit, call.response)
            call.setResponse(response)
            proceedWith(call)
        }

        with(userConfig) {
            config.install(HttpRequestLifecycle)
            config.install(BodyProgress)
            config.install(SaveBodyPlugin)

            if (useDefaultTransformers) {
                config.install("DefaultTransformers") { defaultTransformers() }
            }

            config.install(HttpSend)
            config.install(HttpCallValidator)

            if (followRedirects) {
                config.install(HttpRedirect)
            }

            config += this

            if (useDefaultTransformers) {
                config.install(HttpPlainText)
            }

            config.addDefaultResponseValidation()

            config.install(this@HttpClient)
        }

        responsePipeline.intercept(HttpResponsePipeline.Receive) {
            try {
                proceed()
            } catch (cause: Throwable) {
                monitor.raise(HttpResponseReceiveFailed, HttpResponseReceiveFail(context.response, cause))
                throw cause
            }
        }
    }

    /**
     * Creates a new [HttpClientCall] from a request [builder].
     */
    internal suspend fun execute(builder: HttpRequestBuilder): HttpClientCall {
        monitor.raise(HttpRequestCreated, builder)

        return requestPipeline.execute(builder, builder.body) as HttpClientCall
    }

    /**
     * Checks if the specified [capability] is supported by this client.
     */
    public fun isSupported(capability: HttpClientEngineCapability<*>): Boolean {
        return engine.supportedCapabilities.contains(capability)
    }

    /**
     * Returns a new [HttpClient] by copying this client's configuration
     * and additionally configured by the [block] parameter.
     */
    public fun config(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(
        engine,
        HttpClientConfig<HttpClientEngineConfig>().apply {
            plusAssign(userConfig)
            block()
        },
        manageEngine
    )

    /**
     * Initiates the shutdown process for the `HttpClient`. This is a non-blocking call, which
     * means it returns immediately and begins the client closure in the background.
     *
     * ## Usage
     * ```
     * val client = HttpClient()
     * client.close()
     * client.coroutineContext.job.join() // Waits for complete termination if necessary
     * ```
     *
     * ## Important Notes
     * - **Non-blocking**: `close()` only starts the closing process and does not wait for it to complete.
     * - **Coroutine Context**: To wait for all client resources to be freed, use `client.coroutineContext.job.join()`
     *   or `client.coroutineContext.cancel()` to terminate ongoing tasks.
     * - **Manual Engine Management**: If a custom `engine` was manually created, it must be closed explicitly
     *   after calling `client.close()` to release all resources.
     *
     * Example with custom engine management:
     * ```
     * val engine = HttpClientEngine() // Custom engine instance
     * val client = HttpClient(engine)
     *
     * client.close()
     * engine.close() // Ensure manually created engine is also closed
     * ```
     */
    override fun close() {
        val success = closed.compareAndSet(false, true)
        if (!success) return

        val installedFeatures = attributes[PLUGIN_INSTALLED_LIST]
        installedFeatures.allKeys.forEach { key ->
            @Suppress("UNCHECKED_CAST")
            val plugin = installedFeatures[key as AttributeKey<Any>]

            if (plugin is Closeable) {
                plugin.close()
            }
        }

        clientJob.complete()
        if (manageEngine) {
            engine.close()
        }
    }

    override fun toString(): String = "HttpClient[$engine]"
}
