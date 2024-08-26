/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION_ERROR")
class OriginConnectionPointTest {
    @Test
    fun testDirectRequest() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.local) {
                    assertEquals("localhost", host)
                    assertEquals("localhost", localHost)
                    assertEquals("localhost", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                    assertEquals("localhost", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                with(call.request.origin) {
                    assertEquals("localhost", host)
                    assertEquals("localhost", localHost)
                    assertEquals("localhost", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                    assertEquals("localhost", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/")
    }

    @Test
    fun testProxyXForwardedFor() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("localhost", host)
                    assertEquals("localhost", localHost)
                    assertEquals("localhost", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedFor, "client, proxy")
        }
    }

    @Test
    fun testProxyXForwardedHostNoPort() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("host", serverHost)
                    assertEquals("localhost", localHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                    assertEquals("localhost", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedHost, "host")
        }
    }

    @Test
    fun testProxyXForwardedHostWithPort() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(90, port)
                    assertEquals(80, localPort)
                    assertEquals(90, serverPort)
                    assertEquals("localhost", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedHost, "host:90")
        }
    }

    @Test
    fun testProxyXForwardedScheme() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("localhost", host)
                    assertEquals("localhost", localHost)
                    assertEquals("localhost", serverHost)
                    assertEquals(443, port)
                    assertEquals(80, localPort)
                    assertEquals(443, serverPort)
                    assertEquals("localhost", remoteHost)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
        }
    }

    @Test
    fun testProxyXForwardedSchemeWithPort() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(90, port)
                    assertEquals(80, localPort)
                    assertEquals(90, serverPort)
                    assertEquals("localhost", remoteHost)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "host:90")
        }
    }

    @Test
    fun testProxyXForwardedSchemeNoPort() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(443, port)
                    assertEquals(80, localPort)
                    assertEquals(443, serverPort)
                    assertEquals("localhost", remoteHost)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
            header(HttpHeaders.XForwardedHost, "host")
        }
    }

    @Test
    fun testProxyXForwardedPort() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(91, port)
                    assertEquals(80, localPort)
                    assertEquals(91, serverPort)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-Port", "91")
        }
    }

    @Test
    fun testProxyXForwardedSchemeWithPortAndXForwardedPort() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(91, port)
                    assertEquals(80, localPort)
                    assertEquals(91, serverPort)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.XForwardedHost, "host:90")
            header("X-Forwarded-Port", "91")
        }
    }

    @Test
    fun testProxyXForwardedHttpsFlagOn() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(443, port)
                    assertEquals(80, localPort)
                    assertEquals(443, serverPort)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-SSL", "on")
        }
    }

    @Test
    fun testProxyXForwardedTakeFirstValueByDefault() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(91, port)
                    assertEquals(80, localPort)
                    assertEquals(91, serverPort)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                    assertEquals("client", remoteHost)
                    assertEquals("host1", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host1", serverHost)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-SSL", "on,of")
            header(HttpHeaders.XForwardedFor, "client, proxy")
            header(HttpHeaders.XForwardedProto, "https,http")
            header("X-Forwarded-Port", "91,92")
            header(HttpHeaders.XForwardedHost, "host1, host2")
        }
    }

    @Test
    fun testProxyXForwardedTakeLastValue() = testApplication {
        install(XForwardedHeaders) {
            useLastProxy()
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(91, port)
                    assertEquals(80, localPort)
                    assertEquals(91, serverPort)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                    assertEquals("client", remoteHost)
                    assertEquals("host1", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host1", serverHost)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-SSL", "of,on")
            header(HttpHeaders.XForwardedFor, "proxy,client")
            header(HttpHeaders.XForwardedProto, "http,https")
            header("X-Forwarded-Port", "92,91")
            header(HttpHeaders.XForwardedHost, "host2, host1")
        }
    }

    @Test
    fun testProxyXForwardedSkipLastProxies() = testApplication {
        install(XForwardedHeaders) {
            skipLastProxies(2)
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(91, port)
                    assertEquals(80, localPort)
                    assertEquals(91, serverPort)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                    assertEquals("client", remoteHost)
                    assertEquals("host1", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host1", serverHost)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-SSL", "on,of,of")
            header(HttpHeaders.XForwardedFor, "client, proxy, proxy2")
            header(HttpHeaders.XForwardedProto, "https,http,http")
            header("X-Forwarded-Port", "91,92,93")
            header(HttpHeaders.XForwardedHost, "host1, host2, host3")
        }
    }

    @Test
    fun testProxyXForwardedSkipLastProxiesTakesLastIfNotEnoughValues() = testApplication {
        install(XForwardedHeaders) {
            skipLastProxies(4)
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(93, port)
                    assertEquals(80, localPort)
                    assertEquals(93, serverPort)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                    assertEquals("proxy2", remoteHost)
                    assertEquals("host3", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host3", serverHost)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-SSL", "on,of,of")
            header(HttpHeaders.XForwardedFor, "client, proxy, proxy2")
            header(HttpHeaders.XForwardedProto, "https,http,http")
            header("X-Forwarded-Port", "91,92,93")
            header(HttpHeaders.XForwardedHost, "host1, host2, host3")
        }
    }

    @Test
    fun testProxyXForwardedSkipKnownProxiesAllValues() = testApplication {
        install(XForwardedHeaders) {
            skipKnownProxies(listOf("proxy", "proxy2"))
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(91, port)
                    assertEquals(80, localPort)
                    assertEquals(91, serverPort)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                    assertEquals("client", remoteHost)
                    assertEquals("host1", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host1", serverHost)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-SSL", "on,of,of")
            header(HttpHeaders.XForwardedFor, "client, proxy, proxy2")
            header(HttpHeaders.XForwardedProto, "https,http,http")
            header("X-Forwarded-Port", "91,92,93")
            header(HttpHeaders.XForwardedHost, "host1, host2, host3")
        }
    }

    @Test
    fun testProxyXForwardedSkipKnownProxiesMissingValues() = testApplication {
        install(XForwardedHeaders) {
            skipKnownProxies(listOf("missing", "proxy2"))
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(91, port)
                    assertEquals(80, localPort)
                    assertEquals(91, serverPort)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                    assertEquals("client", remoteHost)
                    assertEquals("host1", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host1", serverHost)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-SSL", "on,of")
            header(HttpHeaders.XForwardedFor, "client, proxy2")
            header(HttpHeaders.XForwardedProto, "https,http")
            header("X-Forwarded-Port", "91,93")
            header(HttpHeaders.XForwardedHost, "host1, host3")
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHost() = testApplication {
        install(ForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Forwarded, "for=client;host=host")
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHostAndPort() = testApplication {
        install(ForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(90, port)
                    assertEquals(80, localPort)
                    assertEquals(90, serverPort)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Forwarded, "for=client;host=host:90")
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHostAndProto() = testApplication {
        install(ForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(443, port)
                    assertEquals(80, localPort)
                    assertEquals(443, serverPort)
                    assertEquals("client", remoteHost)
                    assertEquals("https", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Forwarded, "for=client;proto=https;host=host")
        }
    }

    @Test
    fun testProxyForwardedPerRFCNoHost() = testApplication {
        install(ForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("localhost", host)
                    assertEquals("localhost", localHost)
                    assertEquals("localhost", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Forwarded, "for=client")
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHostMultiple() = testApplication {
        install(ForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Forwarded, "for=client;host=host,for=proxy;host=internal-host")
        }
    }

    @Test
    fun testProxyForwardedTakeFirstValue() = testApplication {
        install(ForwardedHeaders) {
            useFirstValue()
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(80, port)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Forwarded, "for=client;host=host,for=proxy;host=internal-host")
        }
    }

    @Test
    fun testProxyForwardedTakeLastValue() = testApplication {
        install(ForwardedHeaders) {
            useLastValue()
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(80, port)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Forwarded, "for=proxy;host=internal-host,for=client;host=host")
        }
    }

    @Test
    fun testProxyForwardedSkipLastProxies() = testApplication {
        install(ForwardedHeaders) {
            skipLastProxies(2)
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(80, port)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(
                HttpHeaders.Forwarded,
                "for=client;host=host,for=proxy;host=internal-host,for=proxy1;host=internal-host-1"
            )
        }
    }

    @Test
    fun testProxyForwardedSkipLastProxiesTakesLastIfNotEnoughValues() = testApplication {
        install(ForwardedHeaders) {
            skipLastProxies(4)
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("internal-host-1", host)
                    assertEquals(80, port)
                    assertEquals("proxy1", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(
                HttpHeaders.Forwarded,
                "for=client;host=host,for=proxy;host=internal-host,for=proxy1;host=internal-host-1"
            )
        }
    }

    @Test
    fun testProxyForwardedSkipKnownProxiesAllValues() = testApplication {
        install(ForwardedHeaders) {
            skipKnownProxies(listOf("proxy", "proxy2"))
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(80, port)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(
                HttpHeaders.Forwarded,
                "for=client;host=host,for=proxy;host=internal-host,for=proxy2;host=internal-host-1"
            )
        }
    }

    @Test
    fun testProxyForwardedSkipKnownProxiesMissingValues() = testApplication {
        install(ForwardedHeaders) {
            skipKnownProxies(listOf("missing", "proxy2"))
        }
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(80, port)
                    assertEquals("client", remoteHost)
                    assertEquals("http", scheme)
                    assertEquals("HTTP/1.1", version)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Forwarded, "for=client;host=host,for=proxy2;host=internal-host")
        }
    }

    @Test
    fun testOriginWithNoPlugins() = testApplication {
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                }

                call.respond("OK")
            }
            get("/90") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(90, serverPort)
                }

                call.respond("OK")
            }
            get("/no-header") {
                with(call.request.origin) {
                    assertEquals("localhost", host)
                    assertEquals("localhost", localHost)
                    assertEquals("localhost", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header(HttpHeaders.Host, "host")
        }

        client.get("/") {
            header(HttpHeaders.Host, "host:80")
        }

        client.get("/90") {
            header(HttpHeaders.Host, "host:90")
        }

        client.get("/no-header") {
        }
    }

    @Test
    fun testProxyXForwardedPortList() = testApplication {
        install(XForwardedHeaders)
        routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals(91, port)
                    assertEquals(80, localPort)
                    assertEquals(91, serverPort)
                }

                call.respond("OK")
            }
        }

        client.get("/") {
            header("X-Forwarded-Port", "91, 90,95")
        }
    }

    @Test
    fun testXForwardedHeadersSetRemoteAddress() = testApplication {
        application {
            install(XForwardedHeaders)

            routing {
                get("/ipv4-address") {
                    assertEquals("192.168.0.1", call.request.origin.remoteAddress)
                    assertEquals("192.168.0.1", call.request.origin.remoteHost)
                }
                get("/ipv6-address") {
                    assertEquals("2001:db8::1", call.request.origin.remoteAddress)
                    assertEquals("2001:db8::1", call.request.origin.remoteHost)
                }
            }
        }

        client.get("/ipv4-address") {
            header(HttpHeaders.XForwardedFor, "192.168.0.1")
        }
        client.get("/ipv6-address") {
            header(HttpHeaders.XForwardedFor, "2001:db8::1")
        }
    }

    @Test
    fun testForwardedHeadersSetRemoteAddress() = testApplication {
        application {
            install(ForwardedHeaders)

            routing {
                get("/ipv4-address") {
                    assertEquals("192.168.0.1", call.request.origin.remoteAddress)
                    assertEquals("192.168.0.1", call.request.origin.remoteHost)
                }
                get("/ipv6-address") {
                    assertEquals("2001:db8::1", call.request.origin.remoteAddress)
                    assertEquals("2001:db8::1", call.request.origin.remoteHost)
                }
            }
        }

        client.get("/ipv4-address") {
            header(HttpHeaders.Forwarded, "for=192.168.0.1")
        }
        client.get("/ipv6-address") {
            header(HttpHeaders.Forwarded, "for=2001:db8::1")
        }
    }
}
