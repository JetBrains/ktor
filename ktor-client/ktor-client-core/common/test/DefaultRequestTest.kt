
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import kotlin.test.*

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class DefaultRequestTest {

    @Test
    fun testDefaultPathWithDir() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {
                url("http://base.url/path/")
            }
        }

        assertEquals("http://base.url/path/", client.get { }.bodyAsText())
        assertEquals("http://base.url/", client.get("/").bodyAsText())
        assertEquals("http://base.url/path/file", client.get("file").bodyAsText())
        assertEquals("http://base.url/other_path", client.get("/other_path").bodyAsText())
        assertEquals("http://other.host/other_path", client.get("//other.host/other_path").bodyAsText())
    }

    @Test
    fun testDefaultPathWithFile() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {
                url("http://base.url/path/default_file")
            }
        }

        assertEquals("http://base.url/path/default_file", client.get {}.bodyAsText())
        assertEquals("http://base.url/", client.get("/").bodyAsText())
        assertEquals("http://base.url/path/file", client.get("file").bodyAsText())
        assertEquals("http://base.url/other_path", client.get("/other_path").bodyAsText())
        assertEquals("http://other.host/other_path", client.get("//other.host/other_path").bodyAsText())
    }

    @Test
    fun testDefaultWithoutUrl() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {}
        }

        assertEquals(URLBuilder.Companion.origin, client.get {}.bodyAsText())
        assertEquals("${URLBuilder.Companion.origin}/", client.get("/").bodyAsText())
        assertEquals("${URLBuilder.Companion.origin}/file", client.get("file").bodyAsText())
        assertEquals("${URLBuilder.Companion.origin}/other_path", client.get("/other_path").bodyAsText())
        assertEquals("http://other.host/other_path", client.get("//other.host:80/other_path").bodyAsText())
    }

    @Test
    fun testDefaultHostAndPort() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {
                host = "default.host"
                port = 1234
            }
        }

        assertEquals("http://default.host:1234", client.get {}.bodyAsText())
        assertEquals("http://default.host:2345", client.get { url(port = 2345) }.bodyAsText())
        assertEquals("http://other.host:2345/", client.get("//other.host:2345/").bodyAsText())
    }

    @Test
    fun testDefaultProtocol() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {
                url.protocol = URLProtocol.HTTPS
            }
        }

        val defaultUrl = Url(URLBuilder.Companion.origin)
        if (defaultUrl.port == defaultUrl.protocol.defaultPort) {
            assertEquals("https://localhost", client.get {}.bodyAsText())
            assertEquals("ws://localhost:443", client.get { url(scheme = "ws") }.bodyAsText())
        } else {
            assertEquals("https://${defaultUrl.hostWithPort}", client.get {}.bodyAsText())
            assertEquals("ws://${defaultUrl.hostWithPort}", client.get { url(scheme = "ws") }.bodyAsText())
        }
        assertEquals("https://other.host/", client.get("//other.host/").bodyAsText())
        assertEquals("ws://other.host/", client.get("ws://other.host/").bodyAsText())
        assertEquals("ws://other.host:123", client.get("ws://other.host:123").bodyAsText())
        assertEquals("http://other.host", client.get("http://other.host").bodyAsText())
    }

    @Test
    fun testDefaultNoPortKeepsRequestPort() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {
                headers {
                    append("a", "b")
                }
            }
        }

        assertEquals("https://my-website.com/v2/api", client.get("https://my-website.com/v2/api").bodyAsText())
        assertEquals("https://my-website.com:123/v2/api", client.get("https://my-website.com:123/v2/api").bodyAsText())
    }

    @Test
    fun testDefaultHeader() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        it.headers.getAll("header-1")!!.joinToString() + ", " +
                            it.headers.getAll("header-2")!!.joinToString()
                    )
                }
            }

            defaultRequest {
                headers.append("header-1", "value-default-1")
                headers.appendIfNameAbsent("header-2", "value-default-2")
            }
        }

        assertEquals(
            "value-1, value-default-1, value-2",
            client.get {
                headers["header-1"] = "value-1" // appends
                headers["header-2"] = "value-2" // sets
            }.bodyAsText()
        )
    }

    @Test
    fun testDefaultAttributes() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.attributes[TestAttributeKey])
                }
            }

            defaultRequest {
                setAttributes { put(TestAttributeKey, "default-string") }
            }
        }

        assertEquals("default-string", client.get { }.bodyAsText())
        assertEquals("custom-string", client.get { attributes.put(TestAttributeKey, "custom-string") }.bodyAsText())
    }

    @Test
    fun testDefaultQuery() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.parameters["key"] ?: "missing")
                }
            }

            defaultRequest {
                url.parameters.append("key", "default")
            }
        }

        assertEquals("default", client.get { }.bodyAsText())
        assertEquals("custom", client.get { url.parameters.append("key", "custom") }.bodyAsText())
    }

    @Test
    fun testDefaultFragment() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.fragment.takeIf { it.isNotEmpty() } ?: "missing")
                }
            }

            defaultRequest {
                url.fragment = "default"
            }
        }

        assertEquals("default", client.get { }.bodyAsText())
        assertEquals("custom", client.get { url.fragment = "custom" }.bodyAsText())
    }

    @Test
    fun testCookieSentOnce() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    val cookies: String? = it.headers.getAll("Cookie")?.joinToString("; ")
                    assertEquals("second-cookie=bar; first-cookie=foo", cookies)
                    respondOk()
                }
            }

            install(DefaultRequest) {
                cookie("first-cookie", "foo")
            }
        }

        val request = client.preparePost("/some-route") {
            headers {
                contentType(ContentType.Application.Json)
            }
            cookie("second-cookie", "bar")
        }

        request.execute()
    }
}

private val TestAttributeKey = AttributeKey<String>("TestAttributeKey")
