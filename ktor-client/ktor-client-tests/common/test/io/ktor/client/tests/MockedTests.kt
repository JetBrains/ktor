/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.*
import kotlin.test.*

class MockedTests {
    @Test
    fun testPostWithStringResult() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    respondOk("content")
                }
            }
        }
        test { client ->
            val url = "http://localhost"
            val accessToken = "Hello"
            val text = "{}"
            val response: String = client.post {
                url(url)
                setBody(text)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append(HttpHeaders.ContentType, "application/json")
                }
            }.body()

            assertEquals("content", response)
        }
    }

    @Test
    fun testWithLongJson() = testWithEngine(MockEngine) {
        config {
            install(ContentNegotiation) { json() }

            engine {
                // these differ by one char at the end
                val longJSONString =
                    """{"author": "H. P. Lovecraft", "name": "The Nameless City", "text": "When I drew nigh the nameless city I knew it was accursed. I was traveling in a parched and terrible valley under the moon, and afar I saw it protruding uncannily above the sands as parts of a corpse may protrude from an ill-made grave. Fear spoke from the age-worn stones of this hoary survivor of the deluge, this great-grandfather of the eldest pyramid; and a viewless aura repelled me and bade me retreat from antique and sinister secrets that no man should see, and no man else had dared to see..\nRemote in the desert of Araby lies the nameless city, crumbling and inarticulate, its low walls nearly hidden by the sands of uncounted ages. It must have been thus before the first stones of Memphis were laid, and while the bricks of Babylon were yet unbaked. There is no legend so old as to give it a name, or to recall that it was ever alive; but it is told of in whispers around campfires and muttered about by grandams in the tents of sheiks so that all the tribes shun it without wholly knowing why. It was of this place that Abdul Alhazred the mad poet dreamed of the night before he sang his unexplained couplet:\nThat is not dead which can eternal lie, / And with strange aeons even death may die.\nI should have known that the Arabs had good reason for shunning the nameless city, the city told of in strange tales but seen by no living man, yet I defied them and went into the untrodden waste with my camel. I alone have seen it, and that is why no other face bears such hideous lines of fear as mine; why no other man shivers so horribly when the night wind rattles the windows. When I came upon it in the ghastly stillness of unending sleep it looked at me, chilly from the rays of a cold moon amidst the desert's heat. And as I returned its look I forgot my triumph at finding it, and stopped still with my camel to wait for the dawn.\nFor hours I waited, till the east grew grey and the stars faded, and the grey turned to roseate light edged with gold. I heard a moaning and saw a storm of sand stirring among the antique stones though the sky was clear and the vast reaches of desert still. Then suddenly above the desert's far rim came the blazing edge of the sun, seen through the tiny sandstorm which was passing away, and in my fevered state I fancied that from some remote depth there came a crash of musical metal to hail the fiery disc as Memnon hails it from the banks of the Nile. My ears rang and my imagination seethed as I led my camel slowly across the sand to that unvocal place; that place which I alone of living men had seen.\nIn and out amongst the shapeless foundations of houses and places I wandered, finding never a carving or inscription to tell of these men, if men they were, who built this city and dwelt therein so long ago. The antiquity of the spot was unwholesome, and I longed to encounter some sign or device to prove that the city was indeed fashioned by mankind. There were certain proportions and dimensions in the ruins which I did not like. I had with me many tools, and dug much within the walls of the obliterated edifices; but progress was slow, and nothing significant was revealed. When night and the moon returned I felt a chill wind which brought new fear, so that I did not dare to remain in the city. And as I went outside the antique walls to sleep, a small sighing sandstorm gathered behind me, blowing over the grey stones though the moon was bright and most of the desert still.\nI awakened just at dawn from a pageant of horrible dreams, my ears ringing as from some metallic peal. I saw the sun peering redly through the last gusts of a little sandstorm that hovered over the nameless city, and marked the quietness of the rest of the landscape. Once more I ventured within those brooding ruins that swelled beneath the sand like an ogre under a coverlet, and again dug vainly for relics of the forgotten race. At noon I rested, and in the afternoon I spent much time tracing the walls and bygone streets, and the outlines of the nearly vanish..."}""" // ktlint-disable max-line-length

                val longerJSONString =
                    """{"author": "H. P. Lovecraft", "name": "The Nameless City", "text": "When I drew nigh the nameless city I knew it was accursed. I was traveling in a parched and terrible valley under the moon, and afar I saw it protruding uncannily above the sands as parts of a corpse may protrude from an ill-made grave. Fear spoke from the age-worn stones of this hoary survivor of the deluge, this great-grandfather of the eldest pyramid; and a viewless aura repelled me and bade me retreat from antique and sinister secrets that no man should see, and no man else had dared to see..\nRemote in the desert of Araby lies the nameless city, crumbling and inarticulate, its low walls nearly hidden by the sands of uncounted ages. It must have been thus before the first stones of Memphis were laid, and while the bricks of Babylon were yet unbaked. There is no legend so old as to give it a name, or to recall that it was ever alive; but it is told of in whispers around campfires and muttered about by grandams in the tents of sheiks so that all the tribes shun it without wholly knowing why. It was of this place that Abdul Alhazred the mad poet dreamed of the night before he sang his unexplained couplet:\nThat is not dead which can eternal lie, / And with strange aeons even death may die.\nI should have known that the Arabs had good reason for shunning the nameless city, the city told of in strange tales but seen by no living man, yet I defied them and went into the untrodden waste with my camel. I alone have seen it, and that is why no other face bears such hideous lines of fear as mine; why no other man shivers so horribly when the night wind rattles the windows. When I came upon it in the ghastly stillness of unending sleep it looked at me, chilly from the rays of a cold moon amidst the desert's heat. And as I returned its look I forgot my triumph at finding it, and stopped still with my camel to wait for the dawn.\nFor hours I waited, till the east grew grey and the stars faded, and the grey turned to roseate light edged with gold. I heard a moaning and saw a storm of sand stirring among the antique stones though the sky was clear and the vast reaches of desert still. Then suddenly above the desert's far rim came the blazing edge of the sun, seen through the tiny sandstorm which was passing away, and in my fevered state I fancied that from some remote depth there came a crash of musical metal to hail the fiery disc as Memnon hails it from the banks of the Nile. My ears rang and my imagination seethed as I led my camel slowly across the sand to that unvocal place; that place which I alone of living men had seen.\nIn and out amongst the shapeless foundations of houses and places I wandered, finding never a carving or inscription to tell of these men, if men they were, who built this city and dwelt therein so long ago. The antiquity of the spot was unwholesome, and I longed to encounter some sign or device to prove that the city was indeed fashioned by mankind. There were certain proportions and dimensions in the ruins which I did not like. I had with me many tools, and dug much within the walls of the obliterated edifices; but progress was slow, and nothing significant was revealed. When night and the moon returned I felt a chill wind which brought new fear, so that I did not dare to remain in the city. And as I went outside the antique walls to sleep, a small sighing sandstorm gathered behind me, blowing over the grey stones though the moon was bright and most of the desert still.\nI awakened just at dawn from a pageant of horrible dreams, my ears ringing as from some metallic peal. I saw the sun peering redly through the last gusts of a little sandstorm that hovered over the nameless city, and marked the quietness of the rest of the landscape. Once more I ventured within those brooding ruins that swelled beneath the sand like an ogre under a coverlet, and again dug vainly for relics of the forgotten race. At noon I rested, and in the afternoon I spent much time tracing the walls and bygone streets, and the outlines of the nearly vanishe..."}""" // ktlint-disable max-line-length
                val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))

                addHandler { request ->
                    if (request.url.host == "localhost") {
                        when (request.url.encodedPath) {
                            "/long.json" -> return@addHandler respond(longJSONString, headers = responseHeaders)
                            "/longer.json" -> return@addHandler respond(longerJSONString, headers = responseHeaders)
                        }
                    }
                    error("${request.url} should not be requested")
                }
            }
        }

        test { client ->
            client.get("http://localhost/long.json").body<Book>()
            client.get("http://localhost/longer.json").body<Book>()
        }
    }

    @Test
    fun testUrlEscape() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    if (!request.url.protocol.isSecure()) {
                        return@addHandler respondRedirect(
                            "https://api.deutschebahn.com/freeplan/v1/" +
                                "departureBoard/8000096?date=2020-06-14T20%3A21%3A22"
                        )
                    }

                    assertEquals(
                        "https://api.deutschebahn.com/freeplan/v1/departureBoard/8000096" +
                            "?date=2020-06-14T20%3A21%3A22",
                        request.url.toString()
                    )
                    respondOk()
                }
            }
        }

        test { client ->
            client.get("http://api.deutschebahn.com/freeplan/v1/departureBoard/8000096?date=2020-06-14T20:21:22")
                .body<Unit>()
        }
    }
}

@Serializable
data class Book(val author: String, val name: String, val text: String)
