/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import com.sun.nio.file.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.file.*
import java.util.zip.*
import kotlin.io.path.*
import kotlin.test.*

class StaticContentTest {
    val basedir =
        listOf(File("jvm/test"), File("ktor-server/ktor-server-tests/jvm/test"))
            .map { File(it, "io/ktor/server") }
            .first(File::exists)

    private operator fun File.get(relativePath: String) = File(this, relativePath)

    @Test
    fun testStaticContentBuilder() = withTestApplication {
        application.routing {
            static("files") {
                files(basedir)
            }
            static("selected") {
                staticRootFolder = basedir
                files("plugins")
                file("sessions/SessionTestJvm.kt")
                route("virtual") {
                    default("plugins/StaticContentTest.kt")
                    file("foobar.kt", "sessions/SessionTestJvm.kt")
                }
            }
            static {
                staticRootFolder = basedir
                file("foobar.kt", "sessions/SessionTestJvm.kt")
            }
        }

        // get file from nested folder
        handleRequest(HttpMethod.Get, "/files/plugins/StaticContentTest.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // get file from a subfolder
        handleRequest(HttpMethod.Get, "/selected/StaticContentTest.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can't get up to containing folder
        handleRequest(HttpMethod.Get, "/selected/../plugins/StaticContentTest.kt").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }

        // can serve select file from other dir
        handleRequest(HttpMethod.Get, "/selected/sessions/SessionTestJvm.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can't serve file from other dir that was not published explicitly
        handleRequest(HttpMethod.Get, "/selected/sessions/AutoSerializerTest.kt").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }
        // can't serve dir itself
        handleRequest(HttpMethod.Get, "/selected/sessions").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }
        // can serve file from virtual folder with a renamed file
        handleRequest(HttpMethod.Get, "/selected/virtual/foobar.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can serve dir itself if default was given
        handleRequest(HttpMethod.Get, "/selected/virtual").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can serve mapped file from root folder
        handleRequest(HttpMethod.Get, "/foobar.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
    }

    @Test
    fun testStaticContent() = withTestApplication {
        application.install(ConditionalHeaders)
        application.install(PartialContent)
        application.install(AutoHeadResponse)

        application.routing {
            static {
                resources("io.ktor.server.plugins")
                resources("java.util")
                route("z") {
                    staticBasePackage = "java.util"
                    resource("ArrayList.class")
                }
                files(basedir)
                route("f") {
                    files(basedir)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/StaticContentTest.class").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }

        handleRequest(HttpMethod.Get, "/ArrayList.class")
        handleRequest(HttpMethod.Get, "/z/ArrayList.class")
        handleRequest(HttpMethod.Get, "/ArrayList.class2")

        handleRequest(HttpMethod.Get, "/plugins/StaticContentTest.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
        handleRequest(HttpMethod.Get, "/f/plugins/StaticContentTest.kt").let { result ->
            assertTrue(result.response.status()!!.isSuccess())
        }
    }

    object Immutable : CacheControl(null) {
        override fun toString(): String = "immutable"
    }

    @Test
    fun testStaticFiles() = testApplication {
        routing {
            staticFiles("static", basedir, "/plugins/StaticContentTest.kt") {
                cacheControl {
                    when (it.name) {
                        "PartialContentTest.kt" -> listOf(Immutable, CacheControl.MaxAge(1))
                        else -> emptyList()
                    }
                }
                contentType {
                    when (it.name) {
                        "PartialContentTest.kt" -> ContentType.Application.Json
                        else -> null
                    }
                }
                default("/plugins/AutoHeadResponseJvmTest.kt")
            }
            staticFiles("static_no_index", basedir, null)
        }

        val responseDefault = client.get("static")
        assertEquals(HttpStatusCode.OK, responseDefault.status)
        assertTrue(responseDefault.bodyAsText().contains("class StaticContentTest {"))

        val responseCustom = client.get("static/plugins/PartialContentTest.kt")
        assertEquals(HttpStatusCode.OK, responseCustom.status)
        assertTrue(responseCustom.bodyAsText().contains("class PartialContentTest {"))
        assertEquals(ContentType.Application.Json, responseCustom.contentType())
        assertEquals("immutable, max-age=1", responseCustom.headers[HttpHeaders.CacheControl])

        val responseFile = client.get("static/plugins/CookiesTest.kt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertTrue(responseFile.bodyAsText().contains("class CookiesTest {"))
        assertEquals(ContentType.Application.OctetStream, responseFile.contentType())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val notFound = client.get("static/not-existing")
        assertEquals(HttpStatusCode.OK, notFound.status)
        assertTrue(notFound.bodyAsText().contains("class AutoHeadResponseJvmTest {"))

        val noIndex = client.get("static_no_index")
        assertEquals(HttpStatusCode.NotFound, noIndex.status)
        val fileNoIndex = client.get("static_no_index/plugins/CookiesTest.kt")
        assertEquals(HttpStatusCode.OK, fileNoIndex.status)

        val notFoundNoDefault = client.get("static_no_index/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFoundNoDefault.status)
    }

    @Test
    fun testStaticFilesExtensions() = testApplication {
        routing {
            staticFiles("static", basedir) {
                extensions("kt")
            }
        }

        val responseFile = client.get("static/plugins/CookiesTest.kt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertTrue(responseFile.bodyAsText().contains("class CookiesTest {"))
        assertEquals(ContentType.Application.OctetStream, responseFile.contentType())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val responseFileNoExtension = client.get("static/plugins/CookiesTest")
        assertEquals(HttpStatusCode.OK, responseFileNoExtension.status)
        assertTrue(responseFileNoExtension.bodyAsText().contains("class CookiesTest {"))
        assertEquals(ContentType.Application.OctetStream, responseFileNoExtension.contentType())
        assertNull(responseFileNoExtension.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun testStaticFilesExclude() = testApplication {
        routing {
            staticFiles("static", basedir, "/plugins/StaticContentTest.kt") {
                exclude { it.path.contains("CookiesTest") }
                exclude { it.path.contains("PartialContentTest") }
                extensions("kt")
            }
        }

        val responseFileNoIgnore = client.get("static/plugins/CompressionTest.kt")
        assertEquals(HttpStatusCode.OK, responseFileNoIgnore.status)
        assertTrue(responseFileNoIgnore.bodyAsText().contains("class CompressionTest {"))

        val responseFile = client.get("static/plugins/CookiesTest.kt")
        assertEquals(HttpStatusCode.Forbidden, responseFile.status)

        val responseFileOther = client.get("static/plugins/PartialContentTest.kt")
        assertEquals(HttpStatusCode.Forbidden, responseFileOther.status)

        val responseFileNoExtension = client.get("static/plugins/CookiesTest")
        assertEquals(HttpStatusCode.Forbidden, responseFileNoExtension.status)
    }

    @Test
    fun testStaticFilesPreCompressed() = testApplication {
        val filesDir = Files.createTempDirectory("assets").toFile()
        val tempFile = File(filesDir, "testServeEncodedFile.txt")
        val brFile = File(filesDir, "testServeEncodedFile.txt.br")
        val gzFile = File(filesDir, "testServeEncodedFile.txt.gz")
        val gzOnlyFile = File(filesDir, "gzOnly.txt")
        val gzOnlyFileGz = File(filesDir, "gzOnly.txt.gz")
        tempFile.writeText("temp")
        brFile.writeText("temp.br")
        gzFile.writeText("temp.gz")
        gzOnlyFile.writeText("gzOnly.txt")
        gzOnlyFileGz.writeText("gzOnly.txt.gz")

        routing {
            staticFiles("static", filesDir) {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
            }
        }

        val responseFile = client.get("static/testServeEncodedFile.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("temp", responseFile.bodyAsText())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())

        val responseFileBr = client.get("static/testServeEncodedFile.txt") {
            header(HttpHeaders.AcceptEncoding, "br")
        }
        assertEquals(HttpStatusCode.OK, responseFileBr.status)
        assertEquals("temp.br", responseFileBr.bodyAsText())
        assertEquals("br", responseFileBr.headers[HttpHeaders.ContentEncoding])
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())

        val responseFileGz = client.get("static/testServeEncodedFile.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }
        assertEquals(HttpStatusCode.OK, responseFileGz.status)
        assertEquals("temp.gz", responseFileGz.bodyAsText())
        assertEquals("gzip", responseFileGz.headers[HttpHeaders.ContentEncoding])
        assertEquals(ContentType.Text.Plain, responseFileGz.contentType()!!.withoutParameters())

        val responseFileGzOnly = client.get("static/gzOnly.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }
        assertEquals(HttpStatusCode.OK, responseFileGzOnly.status)
        assertEquals("gzOnly.txt.gz", responseFileGzOnly.bodyAsText())
        assertEquals("gzip", responseFileGzOnly.headers[HttpHeaders.ContentEncoding])
        assertEquals(ContentType.Text.Plain, responseFileGzOnly.contentType()!!.withoutParameters())

        val responseFileGzOnlyBr = client.get("static/gzOnly.txt") {
            header(HttpHeaders.AcceptEncoding, "br")
        }
        assertEquals(HttpStatusCode.OK, responseFileGzOnlyBr.status)
        assertEquals("gzOnly.txt", responseFileGzOnlyBr.bodyAsText())
        assertEquals(ContentType.Text.Plain, responseFileGzOnlyBr.contentType()!!.withoutParameters())
    }

    @Test
    fun testStaticFilesAutoHead() = testApplication {
        routing {
            staticFiles("static", basedir, "/plugins/StaticContentTest.kt") {
                enableAutoHeadResponse()
            }
        }

        val responseDefault = client.get("static")
        assertEquals(HttpStatusCode.OK, responseDefault.status)
        assertTrue(responseDefault.bodyAsText().contains("class StaticContentTest {"))

        val responseDefaultHead = client.head("static")
        assertEquals(HttpStatusCode.OK, responseDefaultHead.status)
        assertTrue(responseDefaultHead.bodyAsText().isEmpty())
        assertTrue(responseDefaultHead.contentLength()!! > 0)

        val responseFileHead = client.head("static/plugins/CookiesTest.kt")
        assertEquals(HttpStatusCode.OK, responseFileHead.status)
        assertTrue(responseFileHead.bodyAsText().isEmpty())
        assertTrue(responseFileHead.contentLength()!! > 0)

        val notFound = client.head("static/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFound.status)
    }

    @Test
    fun testStaticFilesModifier() = testApplication {
        routing {
            staticFiles("static", basedir, "/plugins/StaticContentTest.kt") {
                modify { url, call ->
                    call.response.headers.append(HttpHeaders.ETag, url.path.substringAfterLast(File.separatorChar))
                }
            }
        }

        val responseDefault = client.get("static")
        assertEquals(HttpStatusCode.OK, responseDefault.status)
        assertEquals("StaticContentTest.kt", responseDefault.headers[HttpHeaders.ETag])

        val responseFile = client.get("static/plugins/CookiesTest.kt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("CookiesTest.kt", responseFile.headers[HttpHeaders.ETag])
    }

    @Test
    fun testStaticFilesWithIndexAndDefault() = testApplication {
        var respondCount = 0
        install(
            createApplicationPlugin("test") {
                onCallRespond { _ -> respondCount++ }
            }
        )
        routing {
            staticFiles("static", basedir, "/plugins/StaticContentTest.kt") {
                default("/plugins/PartialContentTest.kt")
            }
        }

        val response = client.get("static")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("class StaticContentTest {"))
        assertEquals(1, respondCount)
    }

    @Test
    fun testStaticPath() = testApplication {
        routing {
            staticFileSystem("static", "jvm/test-resources/public") {
                cacheControl {
                    when {
                        it.name.endsWith("file-nested.txt") -> listOf(Immutable, CacheControl.MaxAge(1))
                        else -> emptyList()
                    }
                }
                contentType {
                    when {
                        it.name.endsWith("file-nested.txt") -> ContentType.Application.Json
                        else -> null
                    }
                }
                default("default.txt")
            }
            staticFileSystem("static_no_index", "jvm/test-resources/public", null)
            staticFileSystem("static_no_base_path", null)
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexNested = client.get("static/nested")
        assertEquals(HttpStatusCode.OK, responseIndexNested.status)
        assertEquals("nested index", responseIndexNested.bodyAsText().trim())

        val responseCustom = client.get("static/nested/file-nested.txt")
        assertEquals(HttpStatusCode.OK, responseCustom.status)
        assertEquals("file-nested.txt", responseCustom.bodyAsText().trim())
        assertEquals(ContentType.Application.Json, responseCustom.contentType())
        assertEquals("immutable, max-age=1", responseCustom.headers[HttpHeaders.CacheControl])

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val notFound = client.get("static/not-existing")
        assertEquals(HttpStatusCode.OK, notFound.status)
        assertEquals("default", notFound.bodyAsText().trim())

        val noIndex = client.get("static_no_index")
        assertEquals(HttpStatusCode.NotFound, noIndex.status)
        val fileNoIndex = client.get("static_no_index/file.txt")
        assertEquals(HttpStatusCode.OK, fileNoIndex.status)
        val notFoundNoDefault = client.get("static_no_index/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFoundNoDefault.status)

        val noBasePath = client.get("static_no_base_path/jvm/test-resources/public/file.txt")
        assertEquals(HttpStatusCode.OK, noBasePath.status)
        assertEquals("file.txt", noBasePath.bodyAsText().trim())
        val noBasePathIndex = client.get("static_no_base_path/jvm/test-resources/public")
        assertEquals(HttpStatusCode.OK, noBasePathIndex.status)
        assertEquals("index", noBasePathIndex.bodyAsText().trim())
    }

    @Test
    fun testStaticPathExtensions() = testApplication {
        routing {
            staticFileSystem("static", "jvm/test-resources/public") {
                extensions("txt")
            }
        }

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val responseFileNoExtension = client.get("static/file")
        assertEquals(HttpStatusCode.OK, responseFileNoExtension.status)
        assertEquals("file.txt", responseFileNoExtension.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFileNoExtension.contentType()!!.withoutParameters())
        assertNull(responseFileNoExtension.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun testStaticPathExclude() = testApplication {
        routing {
            staticFileSystem("static", "jvm/test-resources/public") {
                exclude { it.pathString.contains("ignore") }
                extensions("txt")
            }
        }

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val responseIgnoreFile = client.get("static/ignore.txt")
        assertEquals(HttpStatusCode.Forbidden, responseIgnoreFile.status)

        val responseIgnoreFileNoExtension = client.get("static/ignore")
        assertEquals(HttpStatusCode.Forbidden, responseIgnoreFileNoExtension.status)
    }

    @Test
    fun testStaticPathModifier() = testApplication {
        routing {
            staticFileSystem("static", "jvm/test-resources/public") {
                modify { path, call ->
                    call.response.headers.append(HttpHeaders.ETag, path.fileName.toString())
                }
            }
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index.html", responseIndex.headers[HttpHeaders.ETag])

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.headers[HttpHeaders.ETag])
    }

    @Test
    fun testStaticPathAutoHead() = testApplication {
        routing {
            staticFileSystem("static", "jvm/test-resources/public") {
                enableAutoHeadResponse()
            }
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexHead = client.head("static")
        assertEquals(HttpStatusCode.OK, responseIndexHead.status)
        assertTrue(responseIndexHead.bodyAsText().isEmpty())
        assertTrue(responseIndexHead.contentLength()!! > 0)

        val responseFileHead = client.head("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFileHead.status)
        assertTrue(responseFileHead.bodyAsText().isEmpty())
        assertTrue(responseFileHead.contentLength()!! > 0)

        val notFoundHead = client.head("static/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFoundHead.status)
    }

    @Test
    fun testStaticPathPreCompressed() = testApplication {
        routing {
            staticFileSystem("static", "jvm/test-resources/public") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
            }
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexCompressed = client.get("static") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }
        assertEquals(HttpStatusCode.OK, responseIndexCompressed.status)
        assertEquals("index.gz", responseIndexCompressed.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndexCompressed.contentType()!!.withoutParameters())
        assertEquals("gzip", responseIndexCompressed.headers[HttpHeaders.ContentEncoding])

        val responseResource = client.get("static/nested/file-nested.txt")
        assertEquals(HttpStatusCode.OK, responseResource.status)
        assertEquals("file-nested.txt", responseResource.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseResource.contentType()!!.withoutParameters())

        val responseResourceCompressed = client.get("static/nested/file-nested.txt") {
            header(HttpHeaders.AcceptEncoding, "br")
        }
        assertEquals(HttpStatusCode.OK, responseResourceCompressed.status)
        assertEquals("file-nested.txt.br", responseResourceCompressed.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseResourceCompressed.contentType()!!.withoutParameters())
        assertEquals("br", responseResourceCompressed.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun testStaticPathFromZip() = testApplication {
        routing {
            staticZip(
                remotePath = "static",
                basePath = "public",
                zip = Paths.get("jvm/test-resources/public.zip")
            ) {
                default("default.txt")
            }
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexNested = client.get("static/nested")
        assertEquals(HttpStatusCode.OK, responseIndexNested.status)
        assertEquals("nested index", responseIndexNested.bodyAsText().trim())

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val notFound = client.get("static/not-existing")
        assertEquals(HttpStatusCode.OK, notFound.status)
        assertEquals("default", notFound.bodyAsText().trim())
    }

    @Test
    fun testStaticResources() = testApplication {
        routing {
            staticResources("static", "public") {
                cacheControl {
                    when {
                        it.path.endsWith("file-nested.txt") -> listOf(Immutable, CacheControl.MaxAge(1))
                        else -> emptyList()
                    }
                }
                contentType {
                    when {
                        it.path.endsWith("file-nested.txt") -> ContentType.Application.Json
                        else -> null
                    }
                }
                default("default.txt")
            }
            staticResources("static_no_index", "public", null)
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexNested = client.get("static/nested")
        assertEquals(HttpStatusCode.OK, responseIndexNested.status)
        assertEquals("nested index", responseIndexNested.bodyAsText().trim())

        val responseCustom = client.get("static/nested/file-nested.txt")
        assertEquals(HttpStatusCode.OK, responseCustom.status)
        assertEquals("file-nested.txt", responseCustom.bodyAsText().trim())
        assertEquals(ContentType.Application.Json, responseCustom.contentType())
        assertEquals("immutable, max-age=1", responseCustom.headers[HttpHeaders.CacheControl])

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val notFound = client.get("static/not-existing")
        assertEquals(HttpStatusCode.OK, notFound.status)
        assertEquals("default", notFound.bodyAsText().trim())

        val noIndex = client.get("static_no_index")
        assertEquals(HttpStatusCode.NotFound, noIndex.status)
        val fileNoIndex = client.get("static_no_index/file.txt")
        assertEquals(HttpStatusCode.OK, fileNoIndex.status)
        val notFoundNoDefault = client.get("static_no_index/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFoundNoDefault.status)
    }

    @Test
    fun testStaticResourcesExtensions() = testApplication {
        routing {
            staticResources("static", "public") {
                extensions("txt")
            }
        }

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val responseFileNoExtension = client.get("static/file")
        assertEquals(HttpStatusCode.OK, responseFileNoExtension.status)
        assertEquals("file.txt", responseFileNoExtension.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFileNoExtension.contentType()!!.withoutParameters())
        assertNull(responseFileNoExtension.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun testStaticResourcesExclude() = testApplication {
        routing {
            staticResources("static", "public") {
                exclude { it.path.contains("ignore") }
                extensions("txt")
            }
        }

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val responseIgnoreFile = client.get("static/ignore.txt")
        assertEquals(HttpStatusCode.Forbidden, responseIgnoreFile.status)

        val responseIgnoreFileNoExtension = client.get("static/ignore")
        assertEquals(HttpStatusCode.Forbidden, responseIgnoreFileNoExtension.status)
    }

    @Test
    fun testStaticResourcesModifier() = testApplication {
        routing {
            staticResources("static", "public") {
                modify { url, call -> call.response.headers.append(HttpHeaders.ETag, url.path.substringAfterLast('/')) }
            }
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index.html", responseIndex.headers[HttpHeaders.ETag])

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.headers[HttpHeaders.ETag])
    }

    @Test
    fun testStaticResourcesAutoHead() = testApplication {
        routing {
            staticResources("static", "public") {
                enableAutoHeadResponse()
            }
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexHead = client.head("static")
        assertEquals(HttpStatusCode.OK, responseIndexHead.status)
        assertTrue(responseIndexHead.bodyAsText().isEmpty())
        assertTrue(responseIndexHead.contentLength()!! > 0)

        val responseFileHead = client.head("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFileHead.status)
        assertTrue(responseFileHead.bodyAsText().isEmpty())
        assertTrue(responseFileHead.contentLength()!! > 0)

        val notFoundHead = client.head("static/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFoundHead.status)
    }

    @Test
    fun testStaticResourcesPreCompressed() = testApplication {
        routing {
            staticResources("static", "public") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
            }
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexCompressed = client.get("static") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }
        assertEquals(HttpStatusCode.OK, responseIndexCompressed.status)
        assertEquals("index.gz", responseIndexCompressed.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndexCompressed.contentType()!!.withoutParameters())
        assertEquals("gzip", responseIndexCompressed.headers[HttpHeaders.ContentEncoding])

        val responseResource = client.get("static/nested/file-nested.txt")
        assertEquals(HttpStatusCode.OK, responseResource.status)
        assertEquals("file-nested.txt", responseResource.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseResource.contentType()!!.withoutParameters())

        val responseResourceCompressed = client.get("static/nested/file-nested.txt") {
            header(HttpHeaders.AcceptEncoding, "br")
        }
        assertEquals(HttpStatusCode.OK, responseResourceCompressed.status)
        assertEquals("file-nested.txt.br", responseResourceCompressed.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseResourceCompressed.contentType()!!.withoutParameters())
        assertEquals("br", responseResourceCompressed.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun testNullJarFile() = testApplication {
        routing {
            static {
                resources()
            }
        }

        client.get("/").let { result ->
            assertEquals(result.status, HttpStatusCode.NotFound)
        }

        client.get("../build.gradle").let { result ->
            assertEquals(result.status, HttpStatusCode.BadRequest)
        }
    }

    @Test
    fun testStaticContentWrongPath() = withTestApplication {
        application.routing {
            static {
                files(basedir)
            }
        }

        listOf(
            "../build.gradle",
            "../../build.gradle",
            "/../build.gradle",
            "/../../build.gradle",
            "/./.././../build.gradle"
        ).forEach { path ->
            handleRequest(HttpMethod.Get, path).let { result ->
                assertFalse(result.response.status()!!.isSuccess())
            }
        }
    }

    @Test
    fun testServeEncodedFileBr() = withTestApplication {
        val ext = "json"
        val temp = File.createTempFile("testServeEncodedFile", ".$ext.br")

        File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).copyTo(temp, true)

        application.routing {
            static {
                preCompressed {
                    files(temp.parentFile)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/${temp.nameWithoutExtension}") {
            addHeader(HttpHeaders.AcceptEncoding, "br, gzip, deflate, identity")
        }.let { result ->
            assertEquals(temp.readText(), result.response.content)
            assertEquals(ContentType.defaultForFileExtension(ext), result.response.contentType())
            assertEquals("br", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testServeEncodedFileGz() = withTestApplication {
        val ext = "js"
        val temp = File.createTempFile("testServeEncodedFile", ".$ext.gz")

        File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).copyTo(temp, true)

        application.routing {
            static {
                preCompressed {
                    files(temp.parentFile)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/${temp.nameWithoutExtension}") {
            addHeader(HttpHeaders.AcceptEncoding, "br, gzip, deflate, identity")
        }.let { result ->
            assertEquals(temp.readText(), result.response.content)
            assertEquals(ContentType.defaultForFileExtension(ext), result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    // a.k.a testServeEncodedFileGzWithCompressionNoRecompress
    @Test
    fun testSuppressCompressionIfAlreadyCompressed() = withTestApplication {
        application.install(Compression)
        val ext = "js"
        val temp = File.createTempFile("testServeEncodedFile", ".$ext.gz")

        File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).copyTo(temp, true)

        application.routing {
            static {
                preCompressed {
                    files(temp.parentFile)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/${temp.nameWithoutExtension}") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip")
        }.let { result ->
            assertEquals(temp.readText(), result.response.content)
            assertEquals(ContentType.defaultForFileExtension(ext), result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testCompressedTypesOrder() = withTestApplication {
        val ext = "js"
        val cType = ContentType.defaultForFileExtension(ext)

        val tempgz = File.createTempFile("testServeEncodedFile", ".$ext.gz")
        val publicFile = tempgz.nameWithoutExtension
        File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).copyTo(tempgz, true)
        tempgz.copyTo(File(tempgz.parentFile, "$publicFile.br"), true)

        application.routing {
            static("firstgz") {
                preCompressed(CompressedFileType.GZIP, CompressedFileType.BROTLI) {
                    files(tempgz.parentFile)
                }
            }
            static("firstbr") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP) {
                    files(tempgz.parentFile)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/firstgz/$publicFile") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(cType, result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        handleRequest(HttpMethod.Get, "/firstbr/$publicFile") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(cType, result.response.contentType())
            assertEquals("br", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testPreCompressedConfiguresImperatively() = withTestApplication {
        val tempFile = File.createTempFile("testServeEncodedFile", ".dummy")
        val publicFile = tempFile.nameWithoutExtension
        val gzDir = File(tempFile.parentFile, "js").also { it.mkdirs() }
        val brDir = File(tempFile.parentFile, "css").also { it.mkdirs() }

        File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).run {
            copyTo(File(gzDir, "$publicFile.js.gz"), true)
            copyTo(File(brDir, "$publicFile.css.br"), true)
        }

        application.routing {
            static("assets") {
                preCompressed(CompressedFileType.GZIP) {
                    files(gzDir)
                }
                preCompressed(CompressedFileType.BROTLI) {
                    files(brDir)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/assets/$publicFile.js") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.defaultForFileExtension("js"), result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        handleRequest(HttpMethod.Get, "/assets/$publicFile.css") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.defaultForFileExtension("css"), result.response.contentType())
            assertEquals("br", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testPreCompressedConfiguresNested() = withTestApplication {
        val tempFile = File.createTempFile("testServeEncodedFile", ".dummy")
        val publicFile = tempFile.nameWithoutExtension
        val cssDir = File(tempFile.parentFile, "css").also { it.mkdirs() }

        File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).run {
            copyTo(File(cssDir, "$publicFile.js.gz"), true)
            copyTo(File(cssDir, "$publicFile.css.br"), true)
        }

        application.routing {
            static("assets") {
                preCompressed(CompressedFileType.GZIP) {
                    preCompressed(CompressedFileType.BROTLI) {
                        files(cssDir)
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/assets/$publicFile.js") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.defaultForFileExtension("js"), result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        handleRequest(HttpMethod.Get, "/assets/$publicFile.css") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.defaultForFileExtension("css"), result.response.contentType())
            assertEquals("br", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testSendLocalFile() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(
                LocalFileContent(
                    basedir,
                    "/plugins/StaticContentTest.kt".replaceSeparators()
                )
            )
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(
                File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).readText(),
                result.response.content
            )
        }
    }

    @Test
    fun testSendLocalFilePaths() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(
                LocalPathContent(
                    basedir.toPath(),
                    Paths.get("/plugins/StaticContentTest.kt".replaceSeparators())
                )
            )
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(
                File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).readText(),
                result.response.content
            )
        }
    }

    @Test
    fun testSendLocalFileBadRelative() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            assertFailsWithSuspended<Exception> {
                call.respond(
                    LocalFileContent(
                        basedir,
                        "/../../../../../../../../../../../../../etc/passwd"
                    )
                )
            }
            assertFailsWithSuspended<Exception> {
                call.respond(
                    LocalFileContent(
                        basedir,
                        "../../../../../../../../../../../../../etc/passwd"
                    )
                )
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalPathContent(basedir.toPath(), Paths.get("../build.gradle")))
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalPathContent(basedir.toPath(), Paths.get("../../build.gradle")))
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalPathContent(basedir.toPath(), Paths.get("/../build.gradle")))
            }
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }
    }

    @Test
    fun testSendLocalFileBadRelativePaths() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            assertFailsWithSuspended<Exception> {
                call.respond(
                    LocalPathContent(
                        basedir.toPath(),
                        Paths.get("/../../../../../../../../../../../../../etc/passwd")
                    )
                )
            }
            assertFailsWithSuspended<Exception> {
                call.respond(
                    LocalPathContent(
                        basedir.toPath(),
                        Paths.get("../../../../../../../../../../../../../etc/passwd")
                    )
                )
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir, "../build.gradle"))
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir, "../../build.gradle"))
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir, "/../build.gradle"))
            }
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }
    }

    @Test
    fun testInterceptCacheControl() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Plugins) {
            if (call.request.httpMethod == HttpMethod.Get ||
                call.request.httpMethod == HttpMethod.Head
            ) {
                call.response.cacheControl(CacheControl.MaxAge(300))
            }
        }

        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(LocalFileContent(File(basedir, "plugins/StaticContentTest.kt")))
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(
                File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).readText(),
                result.response.content
            )
            assertEquals(listOf("max-age=300"), result.response.headers.values(HttpHeaders.CacheControl))
        }
    }

    @Test
    fun testStaticContentPriority() = withTestApplication {
        application.routing {
            route("/before") {
                get {
                    call.respond("before")
                }
            }
            static("/") {
                defaultResource("index.html", "web-resource")
                resources("web-resource")
            }
            route("/after") {
                get {
                    call.respond("after")
                }
            }
        }

        handleRequest(HttpMethod.Get, "/before").let { result ->
            assertEquals("before", result.response.content)
        }

        handleRequest(HttpMethod.Get, "/after").let { result ->
            assertEquals("after", result.response.content)
        }
    }

    @Test
    fun testPreCompressedResourceServeEncodedResource() = testApplication {
        routing {
            static {
                preCompressed {
                    resources()
                }
            }
        }

        client.get("/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "br")
        }.let { result ->
            assertEquals("br", result.bodyAsText().trim())
            assertEquals(ContentType.Text.Plain, result.contentType()!!.withoutParameters())
            assertEquals("br", result.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }.let { result ->
            assertEquals("gz", result.bodyAsText().trim())
            assertEquals(ContentType.Text.Plain, result.contentType()!!.withoutParameters())
            assertEquals("gzip", result.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/test-resource.txt").let { result ->
            assertEquals("plain", result.bodyAsText().trim())
            assertEquals(ContentType.Text.Plain, result.contentType()!!.withoutParameters())
            assertNull(result.headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun testPreCompressedResourceSuppressCompressionIfAlreadyCompressed() = testApplication {
        install(Compression)

        routing {
            static {
                preCompressed {
                    resources()
                }
            }
        }

        client.get("/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }.let { result ->
            assertEquals("gz", result.bodyAsText().trim())
            assertEquals(ContentType.Text.Plain, result.contentType()!!.withoutParameters())
            assertEquals("gzip", result.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testPreCompressedResourceTypesOrder() = testApplication {
        routing {
            static("firstgz") {
                preCompressed(CompressedFileType.GZIP, CompressedFileType.BROTLI) {
                    resources()
                }
            }
            static("firstbr") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP) {
                    resources()
                }
            }
        }

        client.get("/firstgz/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.Text.Plain, result.contentType()!!.withoutParameters())
            assertEquals("gzip", result.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/firstbr/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.Text.Plain, result.contentType()!!.withoutParameters())
            assertEquals("br", result.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testLocalPathContent() = testApplication {
        routing {
            get("path") {
                call.respond(
                    LocalPathContent(
                        Paths.get("jvm/test-resources/public/file.txt"),
                    )
                )
            }
            get("path-relative") {
                call.respond(
                    LocalPathContent(
                        Paths.get("jvm/test-resources"),
                        Paths.get("public/file.txt")
                    )
                )
            }
            get("zip") {
                val filePath = Paths.get("jvm/test-resources/public.zip")

                @Suppress("BlockingMethodInNonBlockingContext")
                val fileSystem = FileSystems.newFileSystem(filePath, StaticContentTest::class.java.classLoader)

                val path = fileSystem.getPath("public/nested/file-nested.txt")
                call.respond(LocalPathContent(path))
            }
        }

        val responsePath = client.get("path")
        assertEquals(ContentType.Text.Plain, responsePath.contentType()!!.withoutParameters())
        assertEquals("file.txt", responsePath.bodyAsText().trim())

        val responsePathRelative = client.get("path-relative")
        assertEquals(ContentType.Text.Plain, responsePathRelative.contentType()!!.withoutParameters())
        assertEquals("file.txt", responsePathRelative.bodyAsText().trim())

        val responseZip = client.get("zip")
        assertEquals(ContentType.Text.Plain, responseZip.contentType()!!.withoutParameters())
        assertEquals("file-nested.txt", responseZip.bodyAsText().trim())
    }

    @Test
    fun testCharset() = testApplication {
        val fileName = "file"
        val extensions = mapOf(
            "js" to ContentType.Text.JavaScript,
            "css" to ContentType.Text.CSS,
            "svg" to ContentType.Image.SVG,
            "xml" to ContentType.Application.Xml,
        )

        routing {
            staticResources("/", "public/types")
        }

        extensions.forEach { (extension, contentType) ->
            client.get("/$fileName.$extension").apply {
                assertEquals(contentType.withCharset(Charsets.UTF_8), contentType())
            }
        }
    }

    @Test
    fun testStaticPathFromChangingZip() = testApplication {
        val stringPath = "jvm/test-resources/dynamic.zip"
        val path = Paths.get(stringPath)
        val firstFileName = "firstFile.txt"
        val secondFileName = "secondFile.txt"
        val firstContent = "Hello"
        val secondContent = "World"
        val firstZipFile = createZipFile(stringPath, firstFileName, firstContent)

        routing {
            staticZip(
                remotePath = "static",
                basePath = null,
                zip = path,
            )
        }

        val testWatchService = FileSystems.getDefault().newWatchService()
        path.parent.register(
            testWatchService,
            arrayOf(
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.OVERFLOW
            ),
            SensitivityWatchEventModifier.HIGH
        )

        val firstResponse = client.get("static/$firstFileName")
        assertEquals(HttpStatusCode.OK, firstResponse.status)
        assertEquals(firstContent, firstResponse.bodyAsText())

        firstZipFile.delete()
        val secondZipFile = createZipFile(stringPath, secondFileName, secondContent)

        // Wait for the watch service to detect the change
        testWatchService.take()
        delay(3000)

        val secondResponse = client.get("static/$secondFileName")
        assertEquals(HttpStatusCode.OK, secondResponse.status)
        assertEquals(secondContent, secondResponse.bodyAsText())

        val firstNotFound = client.get("static/$firstFileName")
        assertEquals(HttpStatusCode.NotFound, firstNotFound.status)

        secondZipFile.delete()
    }

    private fun createZipFile(zipFileName: String, fileName: String, content: String): File {
        FileOutputStream(zipFileName).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val zipEntry = ZipEntry(fileName)
                zos.putNextEntry(zipEntry)
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return File(zipFileName)
    }
}

private fun String.replaceSeparators() = replace("/", File.separator)

private suspend inline fun <reified T> assertFailsWithSuspended(noinline block: suspend () -> Unit): T {
    val exceptionClass = T::class.java
    try {
        block()
    } catch (e: Throwable) {
        if (exceptionClass.isInstance(e)) {
            @Suppress("UNCHECKED_CAST")
            return e as T
        }

        @Suppress("INVISIBLE_MEMBER")
        asserter.fail("Expected an exception of type $exceptionClass to be thrown, but was $e")
    }

    @Suppress("INVISIBLE_MEMBER")
    asserter.fail("Expected an exception of type $exceptionClass to be thrown, but was completed successfully.")
}
