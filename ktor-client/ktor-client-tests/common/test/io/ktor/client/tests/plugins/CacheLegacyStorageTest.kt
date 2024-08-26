/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.plugins

import io.ktor.client.call.*
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

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
class CacheLegacyStorageTest : ClientLoader() {

    @Test
    fun testNoStore() = clientTests {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/no-store")

            val first = client.get(url).body<String>()
            assertTrue(privateStorage.findByUrl(url).isEmpty())
            assertTrue(publicStorage.findByUrl(url).isEmpty())

            val second = client.get(url).body<String>()
            assertTrue(privateStorage.findByUrl(url).isEmpty())
            assertTrue(publicStorage.findByUrl(url).isEmpty())

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testNoCache() = clientTests {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/no-cache")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)
            assertEquals(0, privateStorage.findByUrl(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)
            assertEquals(0, privateStorage.findByUrl(url).size)

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testETagCache() = clientTests(listOf("Js")) {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/etag")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            assertEquals(first, second)
        }
    }

    @Test
    fun testLastModified() = clientTests(listOf("Js")) {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/last-modified")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            assertEquals(first, second)
        }
    }

    @Test
    fun testVary() = clientTests(listOf("Js")) {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
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
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
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
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
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
                            override val rawContent get() = response.rawContent
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
    fun testMaxAge() = clientTests {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/max-age")

            val first = client.get(url).body<String>()
            val cache = publicStorage.findByUrl(url)
            assertEquals(1, cache.size)

            val second = client.get(url).body<String>()

            assertEquals(first, second)
            delay(2500)

            val third = client.get(url).body<String>()
            assertNotEquals(first, third)
        }
    }

    @Test
    fun testOnlyIfCached() = clientTests {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
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
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/max-age")

            val original = client.get(url).body<String>()
            val cache = publicStorage.findByUrl(url)
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
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            val url = Url("$TEST_SERVER/cache/etag")

            val first = client.get(url) {
                header(HttpHeaders.CacheControl, "no-store")
            }.body<String>()
            assertEquals(0, publicStorage.findByUrl(url).size)

            val second = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            assertNotEquals(first, second)
        }
    }

    @Test
    fun testNoCacheRequest() = clientTests(listOf("Js")) {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            var requestsCount = 0
            client.sendPipeline.intercept(HttpSendPipeline.Engine) {
                requestsCount++
            }

            val url = Url("$TEST_SERVER/cache/etag?max-age=30")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            val second = client.get(url) {
                header(HttpHeaders.CacheControl, "no-cache")
            }.body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            assertEquals(2, requestsCount)
            assertEquals(first, second)
        }
    }

    @Test
    fun testRequestWithMaxAge0() = clientTests(listOf("Js")) {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            var requestsCount = 0
            client.sendPipeline.intercept(HttpSendPipeline.Engine) {
                requestsCount++
            }

            val url = Url("$TEST_SERVER/cache/etag?max-age=30")

            val first = client.get(url).body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            val second = client.get(url) {
                header(HttpHeaders.CacheControl, "max-age=0")
            }.body<String>()
            assertEquals(1, publicStorage.findByUrl(url).size)

            assertEquals(2, requestsCount)
            assertEquals(first, second)
        }
    }

    @Test
    fun testExpires() = clientTests {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
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
            val cache = publicStorage.findByUrl(url)
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
    fun testPublicAndPrivateCache() = clientTests(listOf("native")) {
        val publicStorage = HttpCacheStorage.Unlimited()
        val privateStorage = HttpCacheStorage.Unlimited()
        config {
            install(HttpCache) {
                this.publicStorage = publicStorage
                this.privateStorage = privateStorage
            }
        }

        test { client ->
            val privateUrl = Url("$TEST_SERVER/cache/private")
            val publicUrl = Url("$TEST_SERVER/cache/public")

            fun publicCache() = publicStorage.findByUrl(publicUrl)
            fun privateCache() = privateStorage.findByUrl(privateUrl)

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
        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = Logger.EMPTY
            }
            install(HttpCache)
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
