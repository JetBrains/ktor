/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.resources

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.jvm.*
import kotlin.test.*

@Suppress("DEPRECATION")
internal fun withResourcesApplication(test: ApplicationTestBuilder.() -> Unit) = testApplication {
    install(Resources)
    test()
}

class ResourcesTest {
    @Resource("/")
    class index

    @Test
    fun resourceWithoutURL() = testApplication {
        install(Resources)
        routing {
            get<index> { index ->
                call.respond(call.application.href(index))
            }
        }
        urlShouldBeHandled(index(), "/")
        urlShouldBeUnhandled("/index")
    }

    @Test
    fun resourceLocal() {
        @Resource("/")
        class indexLocal
        withResourcesApplication {
            routing {
                get<indexLocal> { indexLocal ->
                    call.respond(application.href(indexLocal))
                }
            }
            urlShouldBeHandled(indexLocal(), "/")
            urlShouldBeUnhandled("/index")
        }
    }

    @Resource("/about")
    class about

    @Test
    fun resourceWithURL() = withResourcesApplication {
        routing {
            get<about> { about ->
                call.respond(application.href(about))
            }
        }
        urlShouldBeHandled(about(), "/about")
        urlShouldBeUnhandled("/about/123")
    }

    @Resource("/user/{id}")
    class user(val id: Int)

    @Test
    fun resourceWithPathParam() = withResourcesApplication {
        routing {
            get<user> { user ->
                assertEquals(123, user.id)
                call.respond(application.href(user))
            }
        }

        urlShouldBeHandled(user(123), "/user/123")
        urlShouldBeUnhandled("/user?id=123")
    }

    @Resource("/user/{id}/{name}")
    class named(val id: Int, val name: String)

    @Test
    fun resourceWithUrlencodedPathParam() = withResourcesApplication {
        routing {
            get<named> { named ->
                assertEquals(123, named.id)
                assertEquals("abc def", named.name)
                call.respond(application.href(named))
            }
        }
        urlShouldBeHandled(named(123, "abc def"), "/user/123/abc%20def")
        urlShouldBeUnhandled("/user?id=123")
        urlShouldBeUnhandled("/user/123")
    }

    @Resource("/favorite")
    class favorite(val id: Int)

    @Test
    fun resourceWithQueryParam() = withResourcesApplication {
        routing {
            get<favorite> { favorite ->
                assertEquals(123, favorite.id)
                call.respond(application.href(favorite))
            }
        }
        urlShouldBeHandled(favorite(123), "/favorite?id=123")
        urlShouldBeUnhandled("/favorite/123")
        urlShouldBeUnhandled("/favorite")
    }

    @Resource("/container/{id}")
    class pathContainer(val id: Int) {
        @Resource("/items")
        class items(val container: pathContainer)
    }

    @Test
    fun resourceWithPathParameterAndNestedData() = withResourcesApplication {
        val c = pathContainer(123)
        routing {
            get<pathContainer.items> { items ->
                assertEquals(123, items.container.id)
                call.respond(application.href(items))
            }
        }
        urlShouldBeHandled(pathContainer.items(c), "/container/123/items")
        urlShouldBeUnhandled("/container/items")
        urlShouldBeUnhandled("/container/items?id=123")
    }

    @Resource("/container")
    class queryContainer(val id: Int) {
        @Resource("/items")
        class items(val container: queryContainer)
    }

    @Test
    fun resourceWithQueryParameterAndNestedData() = withResourcesApplication {
        val c = queryContainer(123)
        routing {
            get<queryContainer.items> { items ->
                assertEquals(123, items.container.id)
                call.respond(application.href(items))
            }
        }
        urlShouldBeHandled(queryContainer.items(c), "/container/items?id=123")
        urlShouldBeUnhandled("/container/items")
        urlShouldBeUnhandled("/container/123/items")
    }

    @Resource("/container")
    class optionalName(val id: Int, val optional: String? = null)

    @Test
    fun resourceWithMissingOptionalStringParameter() = withResourcesApplication {
        routing {
            get<optionalName> {
                assertEquals(123, it.id)
                assertNull(it.optional)
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(optionalName(123), "/container?id=123")
        urlShouldBeUnhandled("/container")
        urlShouldBeUnhandled("/container/123")
    }

    @Resource("/container")
    class optionalIndex(val id: Int, val optional: Int = 42)

    @Test
    fun resourceWithMissingOptionalIntParameter() = withResourcesApplication {
        routing {
            get<optionalIndex> {
                assertEquals(123, it.id)
                assertEquals(42, it.optional)
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(optionalIndex(123), "/container?id=123&optional=42")
        urlShouldBeUnhandled("/container")
        urlShouldBeUnhandled("/container/123")
    }

    @Test
    fun resourceWithSpecifiedOptionalQueryParameter() = withResourcesApplication {
        routing {
            get<optionalName> {
                assertEquals(123, it.id)
                assertEquals("text", it.optional)
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(optionalName(123, "text"), "/container?id=123&optional=text")
        urlShouldBeUnhandled("/container")
        urlShouldBeUnhandled("/container/123")
    }

    @Resource("/container/{id?}")
    class optionalContainer(val id: Int? = null) {
        @Resource("/items")
        class items(val parent: optionalContainer, val optional: String? = null)
    }

    @Test
    fun resourceWithOptionalPathAndQueryParameter() = withResourcesApplication {
        routing {
            get<optionalContainer> {
                assertEquals(null, it.id)
                call.respond(application.href(it))
            }
            get<optionalContainer.items> {
                assertEquals("text", it.optional)
                call.respond(application.href(it))
            }
        }

        urlShouldBeHandled(optionalContainer(), "/container")
        urlShouldBeHandled(
            optionalContainer.items(optionalContainer(123), "text"),
            "/container/123/items?optional=text"
        )
    }

    @Resource("/container")
    class simpleContainer {
        @Resource("/items")
        class items(val parent: simpleContainer)
    }

    @Test
    fun resourceWithSimplePathContainerAndItems() = withResourcesApplication {
        routing {
            get<simpleContainer.items> {
                call.respond(application.href(it))
            }
            get<simpleContainer> {
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(simpleContainer.items(simpleContainer()), "/container/items")
        urlShouldBeHandled(simpleContainer(), "/container")
        urlShouldBeUnhandled("/items")
    }

    @Resource("/container/{path...}")
    class tailCard(val path: List<String>)

    @Test
    fun resourceWithTailcard() = withResourcesApplication {
        routing {
            get<tailCard> {
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(tailCard(emptyList()), "/container")
        urlShouldBeHandled(tailCard(listOf("some")), "/container/some")
        urlShouldBeHandled(tailCard(listOf("123", "items")), "/container/123/items")
    }

    @Resource("/")
    class multiquery(val value: List<Int>)

    @Resource("/")
    class multiquery2(val name: List<String>)

    @Test
    fun `resource with multiple query values`() = withResourcesApplication {
        routing {
            get<multiquery> {
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(multiquery(listOf(1, 2, 3)), "/?value=1&value=2&value=3")
    }

    @Test
    fun resourceWithMultipleQueryValuesCanSelectByQueryParams() = withResourcesApplication {
        routing {
            get<multiquery> {
                call.respond("1: ${application.href(it)}")
            }
            get<multiquery2> {
                call.respond("2: ${application.href(it)}")
            }
        }
        urlShouldBeHandled(multiquery(listOf(1)), "1: /?value=1")
    }

    @Test
    fun resourceWithMultipleQueryValuesCanSelectByQueryParams2() = withResourcesApplication {
        routing {
            get<multiquery> {
                call.respond("1: ${application.href(it)}")
            }
            get<multiquery2> {
                call.respond("2: ${application.href(it)}")
            }
        }
        urlShouldBeHandled(multiquery2(listOf("john, mary")), "2: /?name=john%2C+mary")
    }

    @Resource("/")
    class multiqueryWithDefault(val value: List<Int> = emptyList())

    @Test
    fun resourceWithMultipleQueryValuesAndDefault() = withResourcesApplication {
        routing {
            get<multiqueryWithDefault> {
                call.respond("${application.href(it)} ${it.value}")
            }
        }
        urlShouldBeHandled(multiqueryWithDefault(listOf()), "/ []")
    }

    @Resource("/")
    class root

    @Test
    fun resourceRootByClass() = withResourcesApplication {
        routing {
            get<root> {
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(root(), "/")
        urlShouldBeUnhandled("/index")
    }

    @Resource("/help")
    class help

    @Test
    fun resourceByClass() = withResourcesApplication {
        routing {
            get<help> {
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(help(), "/help")
        urlShouldBeUnhandled("/help/123")
    }

    @Resource("/users")
    class users {
        @Resource("/me")
        class me(val parent: users)

        @Resource("/{id}")
        class user(val parent: users, val id: Int)
    }

    @Test
    fun resourceByClassInClass() = withResourcesApplication {
        routing {
            get<users.me> {
                call.respond(application.href(it))
            }

            get<users.user> {
                assertEquals(123, it.id)
                call.respond(application.href(it))
            }
        }
        urlShouldBeHandled(users.me(users()), "/users/me")
        urlShouldBeUnhandled("/users/123")

        urlShouldBeHandled(users.user(users(), 123), "/users/123")
        urlShouldBeUnhandled("/users/me")
    }

    @Resource("/items/{id}")
    class items

    @Test
    fun resourceByClassHasBindArgument() {
        assertFailsWith<IllegalArgumentException> {
            withResourcesApplication {
                HttpRequestBuilder().apply {
                    href(ResourcesFormat(), items, url)
                }
            }
        }
    }

    @Resource("/items/{itemId}/{extra?}")
    class OverlappingPath1(val itemId: Int, val extra: String?)

    @Resource("/items/{extra}")
    class OverlappingPath2(val extra: String)

    @Test
    fun overlappingPathsAreResolvedAsExpected() = withResourcesApplication {
        routing {
            get<OverlappingPath1> {
                call.respond(application.href(it))
            }
            get<OverlappingPath2> {
                call.respond(application.href(it))
            }
        }

        urlShouldBeHandled(OverlappingPath1(1, "Foo"), "/items/1/Foo")
        urlShouldBeUnhandled("/items/1-Foo")
    }

    enum class resourceEnum {
        A, B, C
    }

    @Resource("/")
    class resourceWithEnum(val e: resourceEnum)

    @Test
    fun resourceClassWithEnumValue() = withResourcesApplication {
        routing {
            get<resourceWithEnum> {
                call.respondText(application.href(it))
            }
        }

        urlShouldBeHandled(resourceWithEnum(resourceEnum.A), "/?e=A")
        urlShouldBeHandled(resourceWithEnum(resourceEnum.B), "/?e=B")

        runBlocking {
            assertFalse(client.get("/?e=x").status.isSuccess())
        }
    }

    @Test
    fun resourceParameterMismatchShouldLeadToBadRequestStatus() = withResourcesApplication {
        @Resource("/")
        data class L(val text: String, val number: Int, val longNumber: Long)

        routing {
            get<L> {
                call.respondText(
                    "href = ${application.href(it)} text = ${it.text}, " +
                        "number = ${it.number}, longNumber = ${it.longNumber}"
                )
            }
        }

        urlShouldBeHandled(
            L("abc", 1, 2),
            "href = /?text=abc&number=1&longNumber=2 text = abc, number = 1, longNumber = 2"
        )

        runBlocking {
            assertEquals(HttpStatusCode.BadRequest, client.get("/?number=1&longNumber=2").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/?text=abc&number=z&longNumber=2").status)
            assertEquals(
                HttpStatusCode.BadRequest,
                client.get("/?text=abc&number=${Long.MAX_VALUE}&longNumber=2").status
            )
        }
    }

    @JvmInline
    @Serializable
    value class ValueClass(val value: String)

    @Test
    fun resourceWithUInt() = withResourcesApplication {
        @Resource("/{id}/{valueParam}")
        data class Request(val id: UInt, val query: ULong, val valueParam: ValueClass, val valueQuery: ValueClass)

        routing {
            get<Request> {
                call.respond(application.href(it))
            }
        }

        urlShouldBeHandled(Request(1U, 2U, ValueClass("123"), ValueClass("234")), "/1/123?query=2&valueQuery=234")
    }

    @Test
    fun resourceShouldReturnHttpMethodRouteObject() = withResourcesApplication {
        @Resource("/resource")
        class someResource

        routing {
            get<someResource> { call.respondText("Hi!") }
                .apply { assertIs<HttpMethodRouteSelector>((this as RouteNode).selector) }
            options<someResource> { call.respondText("Hi!") }
                .apply { assertIs<HttpMethodRouteSelector>((this as RouteNode).selector) }
            head<someResource> { call.respondText("Hi!") }
                .apply { assertIs<HttpMethodRouteSelector>((this as RouteNode).selector) }
            post<someResource> { call.respondText("Hi!") }
                .apply { assertIs<HttpMethodRouteSelector>((this as RouteNode).selector) }
            put<someResource> { call.respondText("Hi!") }
                .apply { assertIs<HttpMethodRouteSelector>((this as RouteNode).selector) }
            delete<someResource> { call.respondText("Hi!") }
                .apply { assertIs<HttpMethodRouteSelector>((this as RouteNode).selector) }
            patch<someResource> { call.respondText("Hi!") }
                .apply { assertIs<HttpMethodRouteSelector>((this as RouteNode).selector) }
        }
    }

    @Resource("/body")
    object resourceWithBody

    @Test
    fun resourceWithBody() = withResourcesApplication {
        routing {
            post<resourceWithBody, String> { _, body ->
                call.respondText(body)
            }
            put<resourceWithBody, String> { _, body ->
                call.respondText(body)
            }
            patch<resourceWithBody, String> { _, body ->
                call.respondText(body)
            }
        }

        val body = "test"

        runBlocking {
            assertEquals(client.post("/body") { setBody(body) }.bodyAsText(), body)
            assertEquals(client.put("/body") { setBody(body) }.bodyAsText(), body)
            assertEquals(client.patch("/body") { setBody(body) }.bodyAsText(), body)
        }
    }
}
