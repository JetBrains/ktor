/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.client.tests.plugins

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class CacheTest : ClientLoader() {

    @Test
    fun testNoStore() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/no-store")

            val first = client.get(url).body<String>()
            assertTrue(privateStorage.findAll(url).isEmpty())
            assertTrue(publicStorage.findAll(url).isEmpty())

            val second = client.get(url).body<String>()
            assertTrue(privateStorage.findAll(url).isEmpty())
            assertTrue(publicStorage.findAll(url).isEmpty())

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testNoCache() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/no-cache")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)
            assertEquals(0, privateStorage.findAll(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)
            assertEquals(0, privateStorage.findAll(url).size)

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testETagCache() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/etag")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            assertEquals(first, second)
        }
    }

    @Test
    fun testReuseCacheStorage() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val client1 = client.config { }
            val client2 = client.config { }
            val url = Url("$TEST_SERVER/cache/etag-304")

            val first = client1.get(url)
            val second = client2.get(url)

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.OK, second.status)
            assertEquals(first.body<String>(), second.body<String>())
        }
    }

    @Test
    fun testLastModified() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/last-modified")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            assertEquals(first, second)
        }
    }

    @Test
    fun testVary() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary")

            // first header value from Vary
            val first = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            val second = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, second)

            // second header value from Vary
            val third = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertNotEquals(third, second)

            val fourth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertEquals(third, fourth)

            // first header value from Vary
            val fifth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, fifth)

            // no header value from Vary
            val sixth = client.get(url).body<String>()

            assertNotEquals(sixth, second)
            assertNotEquals(sixth, third)

            val seventh = client.get(url).body<String>()

            assertEquals(sixth, seventh)
        }
    }

    @Test
    fun testVaryStale() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary-stale")

            // first header value from Vary
            val first = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            val second = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, second)

            // second header value from Vary
            val third = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertNotEquals(third, second)

            val fourth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertEquals(third, fourth)

            // first header value from Vary
            val fifth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, fifth)

            // no header value from Vary
            val sixth = client.get(url).body<String>()

            assertNotEquals(sixth, second)
            assertNotEquals(sixth, third)

            val seventh = client.get(url).body<String>()

            assertEquals(sixth, seventh)
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testNoVaryIn304() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            client.receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
                if (response.status == HttpStatusCode.NotModified) {
                    val headers = buildHeaders {
                        response.headers
                            .filter { name, _ ->
                                !name.equals(HttpHeaders.Vary, ignoreCase = true)
                            }
                            .forEach(::appendAll)
                    }
                    proceedWith(
                        object : HttpResponse() {
                            override val call get() = response.call
                            override val content get() = response.content
                            override val coroutineContext get() = response.coroutineContext
                            override val headers = headers
                            override val requestTime get() = response.requestTime
                            override val responseTime get() = response.responseTime
                            override val status get() = response.status
                            override val version get() = response.version
                        }
                    )
                }
            }

            val url = Url("$TEST_SERVER/cache/vary-stale")

            // first header value from Vary
            val first = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            val second = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, second)

            // second header value from Vary
            val third = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertNotEquals(third, second)

            val fourth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "ru")
            }.body<String>()

            assertEquals(third, fourth)

            // first header value from Vary
            val fifth = client.get(url) {
                header(HttpHeaders.ContentLanguage, "en")
            }.body<String>()

            assertEquals(first, fifth)

            // no header value from Vary
            val sixth = client.get(url).body<String>()

            assertNotEquals(sixth, second)
            assertNotEquals(sixth, third)

            val seventh = client.get(url).body<String>()

            assertEquals(sixth, seventh)
        }
    }

    @Test
    fun testSingleVaryAndMultipleVaryWithSameValues() = testWithEngine(MockEngine) {
        class FakeResponse {
            var content: String = ""
            var status: HttpStatusCode = HttpStatusCode.OK
            var headers: Headers = headersOf()
        }

        val fakeResponse = FakeResponse()
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }

            engine {
                addHandler {
                    respond(
                        content = fakeResponse.content,
                        status = fakeResponse.status,
                        headers = buildHeaders {
                            fakeResponse.headers.forEach(::appendAll)
                            append(HttpHeaders.ETag, "W/\"ETAG\"")
                        },
                    )
                }
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary-multiple")
            val vary = listOf(HttpHeaders.Origin, HttpHeaders.Accept)

            // multiple Vary keys
            fakeResponse.content = "Cache"
            fakeResponse.status = HttpStatusCode.OK
            fakeResponse.headers = buildHeaders {
                vary.forEach { append(HttpHeaders.Vary, it) }
            }
            val okBody = client.get(url).body<String>()
            assertEquals(okBody, "Cache")

            // single Vary key with comma separated multiple values
            fakeResponse.content = ""
            fakeResponse.status = HttpStatusCode.NotModified
            fakeResponse.headers = headersOf(HttpHeaders.Vary, vary.joinToString(","))
            val notModifiedBody = client.get(url).body<String>()
            assertEquals(notModifiedBody, okBody)
        }
    }

    @Test
    fun testMaxAge() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/max-age")

            val first = client.get(url).body<String>()
            val cache = publicStorage.findAll(url)
            assertEquals(1, cache.size)

            val second = client.get(url).body<String>()

            assertEquals(first, second)
            delay(2500)

            val third = client.get(url).body<String>()
            assertNotEquals(first, third)
        }
    }

    @Test
    fun testSMaxAgeWhenIsSharedTrue() = testWithEngine(MockEngine) {
        class FakeResponse {
            var content: String = ""
            var status: HttpStatusCode = HttpStatusCode.OK
        }

        val fakeResponse = FakeResponse()
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)

                isShared = true
            }

            engine {
                addHandler {
                    respond(
                        content = fakeResponse.content,
                        status = fakeResponse.status,
                        headers = buildHeaders {
                            append(HttpHeaders.ETag, "W/\"ETAG\"")
                            append(HttpHeaders.CacheControl, "max-age=0,s-maxage=1")
                        },
                    )
                }
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/vary-s-maxage")

            fakeResponse.content = "First"
            fakeResponse.status = HttpStatusCode.OK
            val firstBody = client.get(url).body<String>()
            assertEquals(firstBody, "First")

            // When isShared = true, s-maxage is in used as expiration.
            // s-maxage doesn't expires yet, therefore this response should not be reflected.
            fakeResponse.content = "Second"
            fakeResponse.status = HttpStatusCode.OK
            val secondBody = client.get(url).body<String>()
            assertEquals(secondBody, firstBody)
        }
    }

    @Test
    fun testOnlyIfCached() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/etag?max-age=10")

            val responseNoCache = client.get(url) {
                header(HttpHeaders.CacheControl, "only-if-cached")
            }
            assertEquals(HttpStatusCode.GatewayTimeout, responseNoCache.status)

            val bodyOriginal = client.get(url).bodyAsText()

            val responseCached = client.get(url) {
                header(HttpHeaders.CacheControl, "only-if-cached")
            }
            val bodyCached = responseCached.bodyAsText()
            assertEquals(HttpStatusCode.OK, responseCached.status)
            assertEquals(bodyOriginal, bodyCached)
        }
    }

    @Test
    fun testMaxStale() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/max-age")

            val original = client.get(url).body<String>()
            val cache = publicStorage.findAll(url)
            assertEquals(1, cache.size)

            delay(2500)

            val stale = client.get(url) {
                header(HttpHeaders.CacheControl, "max-stale=4")
            }
            assertEquals("110", stale.headers[HttpHeaders.Warning])
            val staleBody = stale.body<String>()
            assertEquals(original, staleBody)

            val staleMaxInt = client.get(url) {
                header(HttpHeaders.CacheControl, "max-stale=${Int.MAX_VALUE}")
            }
            assertEquals("110", stale.headers[HttpHeaders.Warning])
            val staleMaxIntBody = staleMaxInt.body<String>()
            assertEquals(original, staleMaxIntBody)

            val notStale = client.get(url)
            val notStaleBody = notStale.body<String>()
            assertNull(notStale.headers[HttpHeaders.Warning])
            assertNotEquals(original, notStaleBody)
        }
    }

    @Test
    fun testNoStoreRequest() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/etag")

            val first = client.get(url) {
                header(HttpHeaders.CacheControl, "no-store")
            }.body<String>()
            assertEquals(0, publicStorage.findAll(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testNoCacheRequest() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            var requestsCount = 0
            client.sendPipeline.intercept(HttpSendPipeline.Engine) {
                requestsCount++
            }

            val url = Url("$TEST_SERVER/cache/etag?max-age=30")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            val second = client.get(url) {
                header(HttpHeaders.CacheControl, "no-cache")
            }.body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            assertEquals(2, requestsCount)
            assertEquals(first, second)
        }
    }

    @Test
    fun testRequestWithMaxAge0() = clientTests(listOf("Js")) {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            var requestsCount = 0
            client.sendPipeline.intercept(HttpSendPipeline.Engine) {
                requestsCount++
            }

            val url = Url("$TEST_SERVER/cache/etag?max-age=30")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            val second = client.get(url) {
                header(HttpHeaders.CacheControl, "max-age=0")
            }.body<String>()
            assertEquals(1, publicStorage.findAll(url).size)

            assertEquals(2, requestsCount)
            assertEquals(first, second)
        }
    }

    @Test
    fun testExpires() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val now = GMTDate() + 2000L
            val url = Url("$TEST_SERVER/cache/expires")

            suspend fun getWithHeader(expires: String): String {
                delayGMTDate(1)

                return client.get(url) {
                    header("X-Expires", expires)
                }.body()
            }

            val first = getWithHeader(now.toHttpDate())
            val cache = publicStorage.findAll(url)
            assertEquals(1, cache.size)

            // this should be from the cache
            val second = client.get(url).body<String>()

            assertEquals(first, second)
            delay(2500)

            // now it should be already expired
            val third = client.get(url).body<String>()
            assertNotEquals(first, third)

            // illegal values: broken, "0" and blank should be treated as already expired
            // so shouldn't be cached
            var previous = third
            getWithHeader("broken-date").let { result ->
                assertNotEquals(previous, result)
                previous = result
            }

            getWithHeader("0").let { result ->
                assertNotEquals(previous, result)
                previous = result
            }

            getWithHeader(" ").let { result ->
                assertNotEquals(previous, result)
                previous = result
            }

            delayGMTDate(1)
            val last = client.get(url).body<String>()
            assertNotEquals(previous, last)
        }
    }

    @Test
    fun testPublicAndPrivateCache() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }

        test { client ->
            val privateUrl = Url("$TEST_SERVER/cache/private")
            val publicUrl = Url("$TEST_SERVER/cache/public")

            suspend fun publicCache() = publicStorage.findAll(publicUrl)
            suspend fun privateCache() = privateStorage.findAll(privateUrl)

            val firstPrivate = client.get(privateUrl).body<String>()
            assertEquals(firstPrivate, "private")
            assertEquals(1, privateCache().size)
            assertEquals(0, publicCache().size)
            val privateCacheEntry = privateCache().first()

            val firstPublic = client.get(publicUrl).body<String>()
            assertEquals(firstPublic, "public")
            assertEquals(1, publicCache().size)
            assertEquals(1, privateCache().size)
            val publicCacheEntry = publicCache().first()

            val secondPrivate = client.get(privateUrl).body<String>()
            assertEquals(secondPrivate, "private")

            assertSame(privateCacheEntry, privateCache().first())

            // Public from cache.
            val secondPublic = client.get(publicUrl).body<String>()
            assertEquals(secondPublic, "public")

            assertEquals(1, privateCache().size)
            assertEquals(1, publicCache().size)

            assertSame(publicCacheEntry, publicCache().first())
        }
    }

    @Test
    fun testWithLogging() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = Logger.EMPTY
            }
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }
        test { client ->
            client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                val savedResponse = response.call.save().response
                proceedWith(savedResponse)
            }
            val result = client.get("$TEST_SERVER/content/chunked-data?size=5000").bodyAsText()
            assertEquals(5000, result.length)
        }
    }

    @Test
    fun testCachesPrivateByDefault() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
            }
        }
        test { client ->
            val privateUrl = Url("$TEST_SERVER/cache/private")

            suspend fun privateCache() = privateStorage.findAll(privateUrl)

            val firstPrivate = client.get(privateUrl).body<String>()
            assertEquals(firstPrivate, "private")
            assertEquals(1, privateCache().size)
        }
    }

    @Test
    fun testDoesntCachesPrivateWhenShared() = clientTests {
        val publicStorage = CacheStorage.Unlimited()
        val privateStorage = CacheStorage.Unlimited()
        config {
            install(HttpCache) {
                publicStorage(publicStorage)
                privateStorage(privateStorage)
                isShared = true
            }
        }
        test { client ->
            val privateUrl = Url("$TEST_SERVER/cache/private")

            suspend fun privateCache() = privateStorage.findAll(privateUrl)
            suspend fun publicCache() = publicStorage.findAll(privateUrl)

            val firstPrivate = client.get(privateUrl).body<String>()
            assertEquals(firstPrivate, "private")
            assertEquals(0, privateCache().size)
        }
    }

    @Test
    fun testMaxAgeMoreThanMaxValue() = clientTests {
        config {
            install(HttpCache)
        }
        test { client ->
            client.get("$TEST_SERVER/cache/set-max-age").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }
    }

    /**
     * Does delay and ensures that the [GMTDate] measurements report at least
     * the specified number of [milliseconds].
     * The reason why it's not the same is that on some platforms time granularity of GMTDate
     * is not that high and could be even larger than a millisecond.
     */
    private suspend fun delayGMTDate(milliseconds: Long) {
        var delayValue = milliseconds

        do {
            val start = GMTDate()
            delay(delayValue)
            val end = GMTDate()
            if (end > start + milliseconds) {
                break
            }
            if (delayValue != 1L) {
                delayValue = 1L
            }
        } while (true)
    }
}
