/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.content

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import kotlin.test.*

class StaticContentResolutionTest {

    private val baseUrl = StaticContentResolutionTest::class.java.classLoader.getResource("testjar.jar")

    @OptIn(InternalAPI::class)
    @Test
    fun testResourceClasspathResourceWithDirectoryInsideJar() {
        val content = resourceClasspathResource(URL("jar:$baseUrl!/testdir"), "testdir") {
            ContentType.defaultForFileExtension(it.path.extension())
        }

        assertNull(content)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testResourceClasspathResourceWithFileInsideJar() {
        val content = resourceClasspathResource(URL("jar:$baseUrl!/testdir/testfile"), "testdir/testfile") {
            ContentType.defaultForFileExtension(it.path.extension())
        }

        assertNotNull(content)
        with(content) {
            val data = String(runBlocking { readFrom().toByteArray() })
            assertEquals("test\n", data)
        }
    }

    @Test
    fun testNoPassTraversalAllowed() = testApplication {
        routing {
            get("/static/{staticPath...}") {
                val path = call.parameters.getAll("staticPath")?.joinToString(File.separator) ?: return@get
                val content = call.resolveResource(path, "public")
                if (content != null) {
                    call.respond(content)
                }
            }
        }

        val allowed = client.get("/static/allowed.txt").bodyAsText()
        assertEquals("allowed", allowed.trim())
        val secret = client.get("/static/../secret.txt")
        assertEquals(HttpStatusCode.BadRequest, secret.status)
        val secretEscaped1 = client.get("/static/\\.\\./secret.txt")
        assertEquals(HttpStatusCode.NotFound, secretEscaped1.status)
        val secretEscaped2 = client.get("/static/^.^./secret.txt")
        assertEquals(HttpStatusCode.NotFound, secretEscaped2.status)
        val secretEncoded = client.get("/static/%2e%2e/secret.txt")
        assertEquals(HttpStatusCode.BadRequest, secretEncoded.status)
    }

    @Test
    fun resourceUrlsAreCached() = testApplication {
        application {
            var callCount = 0
            val countingClassLoader = object : ClassLoader(environment.classLoader) {
                override fun findResources(name: String?) = super.findResources(name).also {
                    callCount++
                }
            }
            repeat(5) {
                resolveResource("test-config.yaml", classLoader = countingClassLoader) {
                    ContentType.defaultForFileExtension("yaml")
                }
            }
            assertEquals(1, callCount)
        }
    }
}
