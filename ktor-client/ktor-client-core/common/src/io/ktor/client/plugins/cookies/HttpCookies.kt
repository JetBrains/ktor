/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cookies

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpCookies")

/**
 * A plugin that allows you to keep cookies between calls in a storage.
 * By default, it uses an in-memory storage, but you can also implement a persistent storage using [CookiesStorage].
 *
 * You can learn more from [Cookies](https://ktor.io/docs/http-cookies.html).
 */
public class HttpCookies internal constructor(
    private val storage: CookiesStorage,
    private val defaults: List<suspend CookiesStorage.() -> Unit>
) : Closeable {
    @OptIn(DelicateCoroutinesApi::class)
    private val initializer: Job = GlobalScope.launch(Dispatchers.Unconfined) {
        defaults.forEach { it(storage) }
    }

    /**
     * Gets all the cookies associated with a specific [requestUrl].
     */
    public suspend fun get(requestUrl: Url): List<Cookie> {
        initializer.join()
        return storage.get(requestUrl)
    }

    /**
     * Adds cookies in a request header (presumably added through [HttpRequestBuilder.cookie]) into storage,
     * so to manage their lifecycle properly.
     */
    internal suspend fun captureHeaderCookies(builder: HttpRequestBuilder) {
        with(builder) {
            val url = builder.url.clone().build()
            val cookies = headers[HttpHeaders.Cookie]?.let { cookieHeader ->
                LOGGER.trace("Saving cookie $cookieHeader for ${builder.url}")
                parseClientCookiesHeader(cookieHeader).map { (name, encodedValue) -> Cookie(name, encodedValue) }
            }
            cookies?.forEach { storage.addCookie(url, it) }
        }
    }

    internal suspend fun sendCookiesWith(builder: HttpRequestBuilder) {
        val cookies = get(builder.url.clone().build())

        with(builder) {
            if (cookies.isNotEmpty()) {
                val cookieHeader = renderClientCookies(cookies)
                headers[HttpHeaders.Cookie] = cookieHeader
                LOGGER.trace("Sending cookie $cookieHeader for ${builder.url}")
            } else {
                headers.remove(HttpHeaders.Cookie)
            }
        }
    }

    internal suspend fun saveCookiesFrom(response: HttpResponse) {
        val url = response.request.url
        response.headers.getAll(HttpHeaders.SetCookie)?.forEach {
            LOGGER.trace("Received cookie $it in response for ${response.call.request.url}")
        }
        response.setCookie().forEach {
            storage.addCookie(url, it)
        }
    }

    override fun close() {
        storage.close()
    }

    /**
     * A configuration for the [HttpCookies] plugin.
     */
    @KtorDsl
    public class Config {
        private val defaults = mutableListOf<suspend CookiesStorage.() -> Unit>()

        /**
         * Specifies a storage used to keep cookies between calls.
         * By default, it uses an initially empty in-memory [AcceptAllCookiesStorage].
         */
        public var storage: CookiesStorage = AcceptAllCookiesStorage()

        /**
         * Registers a [block] that will be called when the configuration is complete the specified [storage].
         * The [block] can potentially add new cookies by calling [CookiesStorage.addCookie].
         */
        public fun default(block: suspend CookiesStorage.() -> Unit) {
            defaults.add(block)
        }

        internal fun build(): HttpCookies = HttpCookies(storage, defaults)
    }

    public companion object : HttpClientPlugin<Config, HttpCookies> {
        override fun prepare(block: Config.() -> Unit): HttpCookies = Config().apply(block).build()

        override val key: AttributeKey<HttpCookies> = AttributeKey("HttpCookies")

        override fun install(plugin: HttpCookies, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                plugin.captureHeaderCookies(context)
            }
            scope.sendPipeline.intercept(HttpSendPipeline.State) {
                plugin.sendCookiesWith(context)
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                plugin.saveCookiesFrom(response)
            }
        }
    }
}

private fun renderClientCookies(cookies: List<Cookie>): String =
    cookies.joinToString("; ", transform = ::renderCookieHeader)

/**
 * Gets all the cookies for the specified [url] for this [HttpClient].
 */
public suspend fun HttpClient.cookies(url: Url): List<Cookie> = pluginOrNull(HttpCookies)?.get(url) ?: emptyList()

/**
 * Gets all the cookies for the specified [urlString] for this [HttpClient].
 */
public suspend fun HttpClient.cookies(urlString: String): List<Cookie> =
    pluginOrNull(HttpCookies)?.get(Url(urlString)) ?: emptyList()

/**
 * Gets the specified [Cookie] by its [name].
 */
public operator fun List<Cookie>.get(name: String): Cookie? = find { it.name == name }
