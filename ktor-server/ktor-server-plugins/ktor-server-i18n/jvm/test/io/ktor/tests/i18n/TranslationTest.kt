/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.i18n

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.i18n.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationTest {

    private val testAvailableLanguages = listOf(
        "en-US",
        "pt-BR",
        "ru-RU"
    )

    @Test
    fun testTranslationToLanguageSpecifiedInAcceptLanguageHeader() = testApplication {
        install(I18n) {
            availableLanguages = testAvailableLanguages
        }

        routing {
            get("/") {
                val value = i18n("some_key")
                call.respond(HttpStatusCode.OK, value)
            }
        }

        val response = client.get("/") {
            header(HttpHeaders.AcceptLanguage, "pt-BR")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("pt-BR", response.headers[HttpHeaders.ContentLanguage])
        val body = response.bodyAsText()
        assertEquals("Portuguese Key", body)
    }

    @Test
    fun testTranslationToDefaultLanguage() = testApplication {
        install(I18n) {
            availableLanguages = testAvailableLanguages
            defaultLanguage = "en-US"
        }

        routing {
            get("/") {
                val value = i18n("some_key")
                call.respond(HttpStatusCode.OK, value)
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("en-US", response.headers[HttpHeaders.ContentLanguage])
        val body = response.bodyAsText()
        assertEquals("English Key", body)
    }

    @Test
    fun testDefaultBundleWhenDefaultLanguageIsNotConfigured() = testApplication {
        install(I18n) {
            availableLanguages = testAvailableLanguages
        }

        routing {
            get("/") {
                val value = i18n("some_key")
                call.respond(HttpStatusCode.OK, value)
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers[HttpHeaders.ContentLanguage])
        val body = response.bodyAsText()
        assertEquals("Default key", body)
    }

    @Test
    fun testTranslationToPreferredLanguage() = testApplication {
        install(I18n) {
            availableLanguages = testAvailableLanguages
        }

        routing {
            get("/") {
                val value = i18n("some_key").fixJava8Encoding()
                call.respond(HttpStatusCode.OK, value)
            }
        }

        val response = client.get("/") {
            header(HttpHeaders.AcceptLanguage, "ru-RU, pt-BR;q=0.8, en-US;q=0.5, da;q=0.3")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ru-RU", response.headers[HttpHeaders.ContentLanguage])
        val contentAsString = response.bodyAsText()
        assertEquals("Русский Ключ", contentAsString)
    }

    /**
     * On Java 8 PropertyResourceBundle requires properties to be encoded in ISO-8859-1,
     * while starting from Java 9 the default encoding is UTF-8.
     */
    private fun String.fixJava8Encoding(): String {
        return if (javaVersion.startsWith("1.8")) {
            // Encode string in UTF-8 instead of ISO-8859-1
            this.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
        } else {
            this
        }
    }
}

private val javaVersion = System.getProperty("java.version")
