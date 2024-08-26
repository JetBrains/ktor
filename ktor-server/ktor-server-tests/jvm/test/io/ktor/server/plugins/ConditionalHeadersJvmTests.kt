/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.io.*
import org.junit.jupiter.params.*
import org.junit.jupiter.params.provider.*
import java.nio.file.*
import java.nio.file.attribute.*
import java.text.*
import java.time.*
import java.util.*
import java.util.stream.*
import kotlin.io.path.*
import kotlin.test.*

class LastModifiedTest {

    companion object {
        @JvmStatic
        fun dateArguments(): Stream<Arguments> =
            Stream.of("GMT", "GMT+1")
                .map(ZoneId::of)
                .map(ZonedDateTime::now)
                .map(Arguments::of)
    }

    private fun withConditionalApplication(
        date: ZonedDateTime,
        body: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        application {
            install(ConditionalHeaders) {
                version { _, _ -> listOf(LastModifiedVersion(date)) }
            }
            routing {
                handle {
                    call.respondText("response")
                }
            }
        }

        runBlocking {
            body()
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testNoHeaders(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/").let { result ->
            assertEquals(HttpStatusCode.OK, result.status)
            assertEquals("response", result.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testSubrouteInstall(date: ZonedDateTime) = testApplication {
        application {
            routing {
                route("1") {
                    install(ConditionalHeaders) {
                        version { _, _ -> listOf(LastModifiedVersion(date)) }
                    }
                    get { call.respond("response") }
                }
                get("2") { call.respond("response") }
            }
        }
        client.get("/1") {
            header(HttpHeaders.IfModifiedSince, date.toHttpDateString())
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }

        client.get("/2") {
            header(HttpHeaders.IfModifiedSince, date.toHttpDateString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testUseHeadersFromResponse(date: ZonedDateTime) = testApplication {
        application {
            install(ConditionalHeaders)
            routing {
                get {
                    call.response.header(HttpHeaders.LastModified, date.toHttpDateString())
                    call.respond("response")
                }
            }
        }
        client.get {
            header(HttpHeaders.IfModifiedSince, date.toHttpDateString())
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }

        client.get {
            header(HttpHeaders.IfModifiedSince, date.minusDays(1).toHttpDateString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfModifiedSinceEq(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfModifiedSinceEqZoned(date: ZonedDateTime) = testApplication {
        application {
            install(ConditionalHeaders) {
                version { _, _ -> listOf(LastModifiedVersion(date)) }
            }
            routing {
                handle {
                    call.respondText("response")
                }
            }
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfModifiedSinceLess(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.minusDays(1).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfModifiedSinceGt(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfUnModifiedSinceEq(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfUnModifiedSinceLess(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.minusDays(1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.PreconditionFailed, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfUnModifiedSinceGt(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfUnmodifiedSinceIllegal(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                "zzz"
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                "zzz"
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                "zzz"
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                "zzz"
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.PreconditionFailed, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfUnmodifiedSinceMultiple(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                ""
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(2).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(2).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.PreconditionFailed, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testIfModifiedSinceMultiple(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                ""
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(2).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(1).toHttpDateString()
            )
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(2).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    fun testBoth(date: ZonedDateTime): Unit = withConditionalApplication(date) {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                ""
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                ""
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.PreconditionFailed, it.status)
            assertEquals("", it.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            // both conditions are not met but actually it is not clear which one should win
            // so we declare the order
            assertEquals("", it.bodyAsText())
        }
    }
}

class LastModifiedVersionTest {
    private fun temporaryDefaultTimezone(timeZone: TimeZone, block: () -> Unit) {
        val originalTimeZone: TimeZone = TimeZone.getDefault()
        TimeZone.setDefault(timeZone)
        try {
            block()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun checkLastModifiedHeaderIsIndependentOfLocalTimezone(
        constructLastModifiedVersion: (Date) -> LastModifiedVersion
    ) {
        // setup: any non-zero-offset-Timezone will do
        temporaryDefaultTimezone(TimeZone.getTimeZone("GMT+08:00")) {
            // guard: local default timezone needs to be different from GMT for the problem to manifest
            assertTrue(
                TimeZone.getDefault().rawOffset != 0,
                "invalid test setup - local timezone is GMT: ${TimeZone.getDefault()}"
            )

            // setup: last modified for file
            val expectedLastModified: Date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").parse("2018-03-04 15:12:23 GMT")

            // setup: object to test
            val lastModifiedVersion = constructLastModifiedVersion(expectedLastModified)

            // setup HeadersBuilder as test spy
            val headersBuilder = HeadersBuilder()

            // exercise
            lastModifiedVersion.appendHeadersTo(headersBuilder)

            // check
            assertEquals("Sun, 04 Mar 2018 15:12:23 GMT", headersBuilder["Last-Modified"])
        }
    }

    @Test
    fun lastModifiedHeaderFromDateIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            LastModifiedVersion(input)
        }
    }

    @Test
    fun lastModifiedHeaderFromLocalDateTimeIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            LastModifiedVersion(ZonedDateTime.ofInstant(input.toInstant(), ZoneId.systemDefault()))
        }
    }

    @TempDir
    lateinit var temporaryFolder: Path

    @Test
    fun lastModifiedHeaderFromFileTimeIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            // setup: create file
            val path: Path = temporaryFolder.resolve("foo.txt").createFile().apply {
                setLastModifiedTime(FileTime.fromMillis(input.time))
            }

            // guard: file lastmodified is actually set as expected
            assertEquals(input.time, path.getLastModifiedTime().toMillis())

            // setup: object to test
            LastModifiedVersion(Files.getLastModifiedTime(path))
        }
    }
}
