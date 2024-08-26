/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RateLimitTest {

    @Test
    fun testLimitsAmountOfRequests() = testApplication {
        install(RateLimit) {
            register {
                rateLimiter(limit = 10, refillPeriod = 5.seconds)
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        repeat(10) {
            client.get("/").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }

        client.get("/").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testDefaultLimiterLimitsAmountOfRequestsWithoutRouteScopedInstall() = testApplication {
        install(RateLimit) {
            global { rateLimiter(limit = 10, refillPeriod = 5.seconds) }
        }
        routing {
            get("/") {
                call.respond("OK")
            }
        }

        repeat(10) {
            client.get("/").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }

        client.get("/").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testLimitsAmountOfRequestsByName() = testApplication {
        install(RateLimit) {
            register(RateLimitName("limit1")) {
                rateLimiter(limit = 10, refillPeriod = 5.seconds)
            }
            register(RateLimitName("limit2")) {
                rateLimiter(limit = 5, refillPeriod = 5.seconds)
            }
            register {
                rateLimiter(limit = 3, refillPeriod = 5.seconds)
            }
        }
        routing {
            rateLimit(RateLimitName("limit1")) {
                get("/a") {
                    call.respond("OK")
                }
            }
            rateLimit(RateLimitName("limit2")) {
                get("/b") {
                    call.respond("OK")
                }
            }
            rateLimit {
                get("/c") {
                    call.respond("OK")
                }
            }
        }

        repeat(10) {
            client.get("/a").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/a").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }

        repeat(5) {
            client.get("/b").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/b").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }

        repeat(3) {
            client.get("/c").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/c").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testDefaultAndCustomLimiters() = testApplication {
        var time = getTimeMillis()
        install(RateLimit) {
            global {
                rateLimiter(limit = 6, refillPeriod = 6.seconds) { time }
            }
            register {
                rateLimiter(limit = 5, refillPeriod = 10.seconds) { time }
            }
        }
        routing {
            get("/default") {
                call.respond("OK")
            }
            route("/custom") {
                rateLimit {
                    get {
                        call.respond("OK")
                    }
                }
            }
        }

        repeat(6) {
            client.get("/default").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/default").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
        client.get("/custom").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }

        time += 7000
        repeat(5) {
            client.get("/custom").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/custom").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testNestedRateLimits() = testApplication {
        var time = getTimeMillis()
        install(RateLimit) {
            register(RateLimitName("limit1")) {
                rateLimiter(limit = 10, refillPeriod = 10.seconds) { time }
            }
            register(RateLimitName("limit2")) {
                rateLimiter(limit = 5, refillPeriod = 6.seconds) { time }
            }
        }
        routing {
            rateLimit(RateLimitName("limit1")) {
                rateLimit(RateLimitName("limit2")) {
                    get("/a") {
                        call.respond("OK")
                    }
                }
            }
        }

        repeat(5) {
            client.get("/a").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/a").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }

        time += 7000
        repeat(4) {
            client.get("/a").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/a").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testNestedRateLimitsWithSameName() = testApplication {
        var time = getTimeMillis()
        install(RateLimit) {
            register(RateLimitName("limit1")) {
                rateLimiter(limit = 10, refillPeriod = 5.seconds) { time }
            }
        }
        routing {
            rateLimit(RateLimitName("limit1")) {
                rateLimit(RateLimitName("limit1")) {
                    get("/a") {
                        call.respond("OK")
                    }
                }
            }
        }

        repeat(10) {
            client.get("/a").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/a").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }

        time += 5000
        repeat(10) {
            client.get("/a").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/a").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testRefreshLimitsAfterPeriodPassed() = testApplication {
        var time = getTimeMillis()
        install(RateLimit) {
            register {
                rateLimiter(limit = 10, refillPeriod = 5.seconds) { time }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        repeat(10) {
            client.get("/").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }

        time += 5.seconds.inWholeMilliseconds

        repeat(10) {
            client.get("/").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testCanSetPreconfiguredLimiter() = testApplication {
        var limiterCalled = false
        val limiter = object : RateLimiter {
            override suspend fun tryConsume(tokens: Int): RateLimiter.State {
                limiterCalled = true
                return RateLimiter.State.Available(10, 10, 10)
            }
        }
        install(RateLimit) {
            register {
                rateLimiter { _, _ -> limiter }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        client.get("/")
        assertTrue(limiterCalled)
    }

    @Test
    fun testCanConfigureWeightOfRequest() = testApplication {
        install(RateLimit) {
            register {
                rateLimiter(limit = 10, refillPeriod = 5.seconds)
                requestWeight { call, _ -> call.request.queryParameters["price"]!!.toInt() }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        repeat(2) {
            client.get("/?price=4").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/?price=3").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
        client.get("/?price=2").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
    }

    @Test
    fun testSeparateLimitsForDifferentRequestKeys() = testApplication {
        install(RateLimit) {
            register {
                rateLimiter(limit = 10, refillPeriod = 5.seconds)
                requestKey { it.request.queryParameters["key"]!! }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        repeat(10) {
            client.get("/?key=1").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        repeat(10) {
            client.get("/?key=2").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/?key=1").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
        client.get("/?key=2").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testCanCreateSeparateLimitersBasedOnRequestKeys() = testApplication {
        install(RateLimit) {
            register {
                rateLimiter { _, key ->
                    RateLimiter.default(if (key == "key1") 10 else 5, 5.seconds)
                }
                requestKey { it.request.queryParameters["key"]!! }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        repeat(10) {
            client.get("/?key=key1").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        repeat(5) {
            client.get("/?key=key2").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/?key=key1").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
        client.get("/?key=key2").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testLimitersSharedForSubRoutesForOneRequestKey() = testApplication {
        install(RateLimit) {
            register {
                rateLimiter { _, _ -> RateLimiter.default(limit = 10, refillPeriod = 5.seconds) }
            }
        }
        routing {
            route("a") {
                rateLimit {
                    get {
                        call.respond("OK")
                    }
                }
            }
            route("b") {
                rateLimit {
                    get {
                        call.respond("OK")
                    }
                }
            }
        }

        repeat(5) {
            client.get("/a").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        repeat(5) {
            client.get("/b").let {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
        client.get("/a").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
        client.get("/b").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
    }

    @Test
    fun testAddsAdditionalHeadersToResponse() = testApplication {
        var time = getTimeMillis()
        install(RateLimit) {
            register {
                rateLimiter(limit = 10, refillPeriod = 5.seconds) { time }
                requestWeight { call, _ -> call.request.queryParameters["price"]!!.toInt() }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        client.get("/?price=8").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("2", it.headers["X-RateLimit-Remaining"])
            assertEquals("10", it.headers["X-RateLimit-Limit"])
            assertEquals(time / 1000 + 5, it.headers["X-RateLimit-Reset"]!!.toLong())
        }
        time += 2000
        client.get("/?price=1").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals("1", it.headers["X-RateLimit-Remaining"])
            assertEquals("10", it.headers["X-RateLimit-Limit"])
            assertEquals(time / 1000 + 3, it.headers["X-RateLimit-Reset"]!!.toLong())
        }
        client.get("/?price=8").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
            assertEquals("3", it.headers[HttpHeaders.RetryAfter])
        }
    }

    @Test
    fun testUnlimited() = testApplication {
        install(RateLimit) {
            global {
                rateLimiter { _, _ -> RateLimiter.Unlimited }
            }
        }
        routing {
            get("/") {
                call.respond("OK")
            }
        }

        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(Int.MAX_VALUE.toString(), it.headers["X-RateLimit-Remaining"])
            assertEquals(Int.MAX_VALUE.toString(), it.headers["X-RateLimit-Limit"])
        }
    }

    @Test
    fun testThrowsOnNoLimiterSpecified() = testApplication {
        install(RateLimit)

        val error = assertFailsWith<IllegalStateException> {
            startApplication()
        }
        assertEquals("At least one provider must be specified", error.message)
    }

    @Test
    fun testThrowsOnInvalidName() = testApplication {
        install(RateLimit) {
            register(RateLimitName("name")) {
                rateLimiter { _, _ -> RateLimiter.default(limit = 10, refillPeriod = 5.seconds) }
            }
        }
        routing {
            rateLimit(RateLimitName("other_name")) {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            startApplication()
        }
        assertEquals(
            "Rate limit provider with name RateLimitName(name=other_name) is not configured. " +
                "Make sure that you install RateLimit plugin before you use it in Routing",
            error.message
        )
    }

    @Test
    fun testThrowsOnMissingDefaultName() = testApplication {
        install(RateLimit) {
            register(RateLimitName("name")) {
                rateLimiter { _, _ -> RateLimiter.default(limit = 10, refillPeriod = 5.seconds) }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            startApplication()
        }
        assertEquals(
            "Rate limit provider with name RateLimitName(name=KTOR_NO_NAME_RATE_LIMITER) is not configured. " +
                "Make sure that you install RateLimit plugin before you use it in Routing",
            error.message
        )
    }

    @Test
    fun testThrowsOnOnlyDefaultName() = testApplication {
        install(RateLimit) {
            register {
                rateLimiter { _, _ -> RateLimiter.default(limit = 10, refillPeriod = 5.seconds) }
            }
        }
        routing {
            rateLimit(RateLimitName("name")) {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            startApplication()
        }
        assertEquals(
            "Rate limit provider with name RateLimitName(name=name) is not configured. " +
                "Make sure that you install RateLimit plugin before you use it in Routing",
            error.message
        )
    }

    @Test
    fun testThrowsOnRegisteringProviderWithSameName() = testApplication {
        install(RateLimit) {
            register(RateLimitName("name")) {
                rateLimiter { _, _ -> RateLimiter.default(limit = 10, refillPeriod = 5.seconds) }
            }
            register(RateLimitName("name")) {
                rateLimiter { _, _ -> RateLimiter.default(limit = 10, refillPeriod = 5.seconds) }
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            startApplication()
        }
        assertEquals("Rate limit provider with name RateLimitName(name=name) is already configured", error.message)
    }

    @Test
    fun testRemovesUnusedRateLimitersOnRefill() = testApplication {
        var createCount = 0
        install(RateLimit) {
            register {
                rateLimiter { _, _ ->
                    createCount++
                    RateLimiter.default(limit = 3, refillPeriod = 1.seconds)
                }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        delay(300)
        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        delay(300)
        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        delay(300)
        client.get("/").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }

        assertEquals(1, createCount)
        delay(200)
        assertEquals(1, createCount)

        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        assertEquals(2, createCount)
    }

    @Test
    fun testRemovesUnusedRateLimitersOnRefillWithRaceCondition() = testApplication {
        var createCount = 0
        val key = AttributeKey<ConcurrentMap<ProviderKey, RateLimiter>>("RateLimiterInstancesRegistryKey")
        val rateLimitersRegistry: ConcurrentMap<ProviderKey, RateLimiter> = ConcurrentMap()
        var time = getTimeMillis()
        application {
            attributes.put(key, rateLimitersRegistry)
        }
        install(RateLimit) {
            register {
                rateLimiter { _, _ ->
                    createCount++
                    RateLimiter.default(limit = 3, refillPeriod = 500.milliseconds) { time }
                }
            }
        }
        routing {
            rateLimit {
                get("/") {
                    call.respond("OK")
                }
            }
        }

        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        time += 60
        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        time += 60
        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        time += 60
        client.get("/").let {
            assertEquals(HttpStatusCode.TooManyRequests, it.status)
        }
        assertEquals(1, rateLimitersRegistry.size)
        rateLimitersRegistry[rateLimitersRegistry.keys.first()] = RateLimiter.default(
            limit = 3,
            refillPeriod = 10.seconds
        )

        assertEquals(1, createCount)
        assertEquals(1, rateLimitersRegistry.size)
        delay(550)
        assertEquals(1, rateLimitersRegistry.size)
        assertEquals(1, createCount)

        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
        }
        assertEquals(1, rateLimitersRegistry.size)
        assertEquals(1, createCount)
    }

    @Test
    fun testCanAccessRateLimitersForCall() = testApplication {
        val key = AttributeKey<List<Pair<RateLimitName, RateLimiter>>>("RateLimitersForCallKey")
        install(RateLimit) {
            register(RateLimitName("limit1")) {
                rateLimiter(limit = 10, refillPeriod = 10.seconds)
            }
            register(RateLimitName("limit2")) {
                rateLimiter(limit = 5, refillPeriod = 6.seconds)
            }
        }
        routing {
            rateLimit(RateLimitName("limit1")) {
                rateLimit(RateLimitName("limit2")) {
                    get("/a") {
                        val rateLimiters = call.attributes[key]
                        call.respond(rateLimiters.joinToString { it.first.toString() })
                    }
                }
            }
        }

        val response = client.get("/a")
        assertEquals("RateLimitName(name=limit1), RateLimitName(name=limit2)", response.bodyAsText())
    }
}
