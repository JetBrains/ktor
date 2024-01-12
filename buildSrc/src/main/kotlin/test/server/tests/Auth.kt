/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import java.security.*

internal fun Application.authTestServer() {
    install(Authentication) {
        basic("test-basic") {
            realm = "my-server"
            validate { call ->
                if (call.name == "user1" && call.password == "Password1") UserIdPrincipal("user1") else null
            }
        }

        digest("digest") {
            val password = "Circle Of Life"
            algorithmName = "MD5"
            realm = "testrealm@host.com"

            digestProvider { userName, realm ->
                digest(MessageDigest.getInstance(algorithmName), "$userName:$realm:$password")
            }
        }

        digest("digest-2") {
            val password = "some password"
            algorithmName = "MD5"
            realm = "testrealm-2@host.com"

            digestProvider { userName, realm ->
                digest(MessageDigest.getInstance(algorithmName), "$userName:$realm:$password")
            }
        }

        digest("digest-SHA256") {
            val password = "Circle Of Life"
            algorithmName = "SHA-256"
            realm = "testrealm@host.com"

            digestProvider { userName, realm ->
                digest(MessageDigest.getInstance(algorithmName), "$userName:$realm:$password")
            }
        }

        basic("basic") {
            validate { credential ->
                check("MyUser" == credential.name)
                check("1234" == credential.password)
                UserIdPrincipal("MyUser")
            }
        }
    }

    routing {
        route("auth") {
            route("basic") {
                authenticate("test-basic") {
                    post {
                        val requestData = call.receiveText()
                        if (requestData == "{\"test\":\"text\"}") {
                            call.respondText("OK")
                        } else {
                            call.respond(HttpStatusCode.BadRequest)
                        }
                    }
                    route("ws") {
                        route("/echo") {
                            webSocket(protocol = "ocpp2.0,ocpp1.6") {
                                for (message in incoming) {
                                    send(message)
                                }
                            }
                        }
                    }
                }
            }

            authenticate("digest") {
                get("digest") {
                    call.respondText("ok")
                }
            }
            authenticate("digest-2") {
                get("digest-2") {
                    call.respondText("ok")
                }
            }
            authenticate("digest-SHA256") {
                get("digest-SHA256") {
                    call.respondText("ok")
                }
            }
            authenticate("basic") {
                get("basic-fixed") {
                    call.respondText("ok")
                }
            }

            get("unauthorized") {
                // simulate a server which responds with 401 and another auth request on bad credentials
                call.response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"TestServer\", charset=UTF-8")
                call.respond(HttpStatusCode.Unauthorized)
            }

            route("bearer") {
                get("test-refresh") {
                    val token = call.request.headers["Authorization"]
                    if (token.isNullOrEmpty() || token.contains("invalid")) {
                        call.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"TestServer\"")
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    call.respond(HttpStatusCode.OK)
                }
                get("test-refresh-no-www-authenticate-header") {
                    val token = call.request.headers["Authorization"]
                    if (token.isNullOrEmpty() || token.contains("invalid")) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    call.respond(HttpStatusCode.OK)
                }
                route("token") {
                    get("first") {
                        call.respond("first")
                    }
                    get("second") {
                        delay(call.parameters["delay"]?.toLong() ?: 0)
                        call.respond("second")
                    }
                    get("refresh-401") {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
                get("first") {
                    val header = call.request.headers[HttpHeaders.Authorization]

                    if (header != "Bearer first") {
                        call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    call.respond("OK")
                }
                get("second") {
                    val header = call.request.headers[HttpHeaders.Authorization]
                    if (header != "Bearer second") {
                        call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    call.respond("OK")
                }
            }

            route("multiple") {
                get("header") {
                    val token = call.request.headers[HttpHeaders.Authorization]

                    if (token.isNullOrEmpty() || token.contains("Invalid")) {
                        call.response.header(
                            HttpHeaders.WWWAuthenticate,
                            "Basic realm=\"TestServer\", charset=UTF-8, Digest, Bearer realm=\"my-server\""
                        )
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    call.respond("OK")
                }
                get("headers") {
                    val token = call.request.headers[HttpHeaders.Authorization]

                    if (token.isNullOrEmpty() || token.contains("Invalid")) {
                        call.response.header(
                            HttpHeaders.WWWAuthenticate,
                            "Basic realm=\"TestServer\", charset=UTF-8, Digest"
                        )
                        call.response.header(
                            HttpHeaders.WWWAuthenticate,
                            "Bearer realm=\"my-server\""
                        )
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }

                    call.respond("OK")
                }
            }
        }
    }
}

private fun digest(digester: MessageDigest, data: String): ByteArray {
    digester.reset()
    digester.update(data.toByteArray(Charsets.ISO_8859_1))
    return digester.digest()
}
