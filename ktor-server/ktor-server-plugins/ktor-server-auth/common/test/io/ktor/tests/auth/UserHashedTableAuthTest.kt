/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.random.*
import kotlin.test.*

class UserHashedTableAuthTest {
    private val randomSaltPart = Random.nextInt(0, 0x10000)
        .toString(radix = 16)
        .padStart(4, '0')

    // for test we don't care of hash stability so we append random part to the hash salt
    // for production, you should keep salt part in secret and never use constant salt
    // please see the documentation for explanation and read related articles for best practices
    private val digestFunction = { text: String -> ("ktor-$randomSaltPart-${text.length}").toByteArray() }

    @Test
    fun testConfigInlined() = testSingle(
        UserHashedTableAuth(
            table = mapOf(
                "test" to digestFunction("test")
            ),
            digester = digestFunction
        )
    )

    private fun testSingle(hashedUserTable: UserHashedTableAuth) = testApplication {
        install(Authentication) {
            form {
                challenge("/unauthorized")
                validate { hashedUserTable.authenticate(it) }
            }
            form("checkOnly") {
                validate { hashedUserTable.authenticate(it) }
            }
        }

        routing {
            authenticate {
                post("/redirect") { call.respondText("ok") }
            }
            authenticate("checkOnly") {
                post("/deny") { call.respondText("ok") }
            }
        }

        handlePost("/deny").let { result ->
            assertEquals(HttpStatusCode.Unauthorized, result.status)
            assertEquals("", result.bodyAsText())
        }
        handlePost("/redirect").let { result ->
            assertEquals(HttpStatusCode.Found, result.status)
            assertEquals("", result.bodyAsText())
        }
        handlePost("/deny?user=test&pass=test").let { result ->
            assertEquals(HttpStatusCode.Unauthorized, result.status)
            assertEquals("", result.bodyAsText())
        }
        handlePost("/deny", "test", "bad-pass").let { result ->
            assertEquals(HttpStatusCode.Unauthorized, result.status)
            assertEquals("", result.bodyAsText())
        }
        handlePost("/deny?bad-user=bad-pass", "test").let { result ->
            assertEquals(HttpStatusCode.Unauthorized, result.status)
            assertEquals("", result.bodyAsText())
        }
        handlePost("/deny", "test", "test").let { result ->
            assertEquals(HttpStatusCode.OK, result.status)
            assertEquals("ok", result.bodyAsText())
        }
    }

    private suspend fun ApplicationTestBuilder.handlePost(
        uri: String,
        user: String? = null,
        password: String? = null
    ): HttpResponse {
        return client.post(uri) {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                ByteReadChannel(
                    Parameters.build {
                        if (user != null) append("user", user)
                        if (password != null) append("password", password)
                    }.formUrlEncode()
                )
            )
        }
    }
}
