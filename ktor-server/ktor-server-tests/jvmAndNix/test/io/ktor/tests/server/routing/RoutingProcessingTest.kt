/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import kotlin.test.*

private enum class SelectedRoute { Get, Param, Header, None }
private class Foo

@Suppress("DEPRECATION")
class RoutingProcessingTest {

    @Test
    fun testRoutingOnGETFooBar() = withTestApplication {
        application.routing {
            get("/foo/bar") {
                call.respond(HttpStatusCode.OK)
            }
        }

        on("making get request to /foo/bar") {
            val result = handleRequest {
                uri = "/foo/bar"
                method = HttpMethod.Get
            }
            it("should have a response with OK status") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
        }

        on("making post request to /foo/bar") {
            val result = handleRequest {
                uri = "/foo/bar"
                method = HttpMethod.Post
            }
            it("should not be handled") {
                assertFalse(result.response.status()!!.isSuccess())
            }
        }
    }

    @Test
    fun testRoutingOnGETUserWithParameter() = withTestApplication {
        var username = ""
        application.routing {
            route("user") {
                param("name") {
                    method(HttpMethod.Get) {
                        handle {
                            username = call.parameters["name"] ?: ""
                        }
                    }
                }
            }
        }
        on("making get request to /user with query parameters") {
            handleRequest {
                uri = "/user?name=john"
                method = HttpMethod.Get
            }
            it("should have extracted username") {
                assertEquals("john", username)
            }
        }
    }

    @Test
    fun testRoutingOnGETUserWithSurroundedParameter() = withTestApplication {
        var username = ""
        application.routing {
            get("/user-{name}-get") {
                username = call.parameters["name"] ?: ""
            }
        }
        on("making get request to /user with query parameters") {
            handleRequest {
                uri = "/user-john-get"
                method = HttpMethod.Get
            }
            it("should have extracted username") {
                assertEquals("john", username)
            }
        }
    }

    @Test
    fun testRoutingOnParamRoute() = withTestApplication {
        var selectedRoute = SelectedRoute.None
        application.routing {
            route("test") {
                param("param") {
                    handle {
                        selectedRoute = SelectedRoute.Param
                    }
                }

                get {
                    selectedRoute = SelectedRoute.Get
                }
            }
        }
        on("making get request to /test with `param` query parameter") {
            handleRequest {
                uri = "/test?param=value"
                method = HttpMethod.Get
            }
            it("should choose param routing") {
                assertEquals(selectedRoute, SelectedRoute.Param)
            }
        }
        on("making get request to /test without `param` query parameter") {
            handleRequest {
                uri = "/test"
                method = HttpMethod.Get
            }
            it("should choose get routing") {
                assertEquals(selectedRoute, SelectedRoute.Get)
            }
        }
    }

    @Test
    fun testRoutingOnOptionalParamRoute() = withTestApplication {
        var selectedRoute = SelectedRoute.None
        application.routing {
            route("test") {
                optionalParam("param") {
                    handle {
                        selectedRoute = SelectedRoute.Param
                    }
                }

                get {
                    selectedRoute = SelectedRoute.Get
                }
            }
        }
        on("making get request to /test with `param` query parameter") {
            handleRequest {
                uri = "/test?param=value"
                method = HttpMethod.Get
            }
            it("should choose param routing") {
                assertEquals(selectedRoute, SelectedRoute.Param)
            }
        }
        on("making get request to /test without `param` query parameter") {
            handleRequest {
                uri = "/test"
                method = HttpMethod.Get
            }
            it("should choose get routing") {
                assertEquals(selectedRoute, SelectedRoute.Get)
            }
        }
    }

    @Test
    fun testRoutingOnRoutesWithSameQualityShouldBeBasedOnOrder() = withTestApplication {
        // `accept {}` and `param {}` use quality = 1.0
        var selectedRoute = SelectedRoute.None
        application.routing {
            route("paramFirst") {
                param("param") {
                    handle {
                        selectedRoute = SelectedRoute.Param
                    }
                }

                accept(ContentType.Text.Plain) {
                    handle {
                        selectedRoute = SelectedRoute.Header
                    }
                }
            }
            route("headerFirst") {
                accept(ContentType.Text.Plain) {
                    handle {
                        selectedRoute = SelectedRoute.Header
                    }
                }

                param("param") {
                    handle {
                        selectedRoute = SelectedRoute.Param
                    }
                }
            }
        }
        on("making request to /paramFirst with `param` query parameter and accept header") {
            handleRequest {
                uri = "/paramFirst?param=value"
                addHeader(HttpHeaders.Accept, "text/plain")
            }
            it("should choose param routing") {
                assertEquals(selectedRoute, SelectedRoute.Param)
            }
        }
        on("making request to /headerFirst with `param` query parameter and accept header") {
            handleRequest {
                uri = "/headerFirst?param=value"
                addHeader(HttpHeaders.Accept, "text/plain")
            }
            it("should choose header routing") {
                assertEquals(selectedRoute, SelectedRoute.Header)
            }
        }
    }

    @Test
    fun testMostSpecificSelected() = withTestApplication {
        var path = ""
        application.routing {
            get("{path...}") {
                path = call.parameters.getAll("path")?.joinToString("/") ?: "/"
            }
            get("z/{path...}") {
                path = "[Z] " + (call.parameters.getAll("path")?.joinToString("/") ?: "/")
            }
        }
        on("making get request to /z/a/b/c") {
            handleRequest {
                uri = "/z/a/b/c"
                method = HttpMethod.Get
            }
            it("should have handled by more specific route") {
                assertEquals("[Z] a/b/c", path)
            }
        }
        on("making get request to /x/a/b/c") {
            handleRequest {
                uri = "/x/a/b/c"
                method = HttpMethod.Get
            }
            it("should have handled by more specific route") {
                assertEquals("x/a/b/c", path)
            }
        }
    }

    private class PluginsWithProceedHook(val phase: PipelinePhase) :
        Hook<suspend PluginsWithProceedHook.Context.() -> Unit> {

        class Context(private val pipelineContext: PipelineContext<*, *>) {
            suspend fun proceed() {
                pipelineContext.proceed()
            }
        }

        override fun install(pipeline: ApplicationCallPipeline, handler: suspend Context.() -> Unit) {
            pipeline.intercept(phase) {
                Context(this).handler()
            }
        }
    }

    @Test
    fun testRoutingOnGETUserUsernameWithInterceptors() = withTestApplication {
        var userIntercepted = false
        var wrappedWithInterceptor = false
        var userName = ""
        var userNameGotWithinInterceptor = false

        application.routing {
            route("user") {
                install(
                    createRouteScopedPlugin("test") {
                        on(PluginsWithProceedHook(ApplicationCallPipeline.Plugins)) {
                            userIntercepted = true
                            wrappedWithInterceptor = true
                            proceed()
                            wrappedWithInterceptor = false
                        }
                    }
                )
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                    userNameGotWithinInterceptor = wrappedWithInterceptor
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(userNameGotWithinInterceptor, "should have processed /user/username in context of interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test
    fun testRouteWithTypedBody(): Unit = withTestApplication {
        application.routing {
            post<String> { answer ->
                assertEquals("42", answer)
            }
            put<String>("/put") { answer ->
                assertEquals("42", answer)
            }
            patch<String>("/patching") { answer ->
                assertEquals("42", answer)
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            setBody("42")
        }
        handleRequest(HttpMethod.Put, "/put") {
            setBody("42")
        }
        handleRequest(HttpMethod.Patch, "/patching") {
            setBody("42")
        }
    }

    @Test
    fun testInterceptionOrderWhenOuterShouldBeAfter() = withTestApplication {
        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var userName = ""
        var routingInterceptorWrapped = false

        application.routing {
            install(
                createRouteScopedPlugin("test") {
                    on(PluginsWithProceedHook(ApplicationCallPipeline.Call)) {
                        wrappedWithInterceptor = true
                        rootIntercepted = true
                        proceed()
                        wrappedWithInterceptor = false
                    }
                }
            )

            route("user") {
                install(
                    createRouteScopedPlugin("test-route") {
                        onCall {
                            userIntercepted = true
                            routingInterceptorWrapped = wrappedWithInterceptor
                        }
                    }
                )
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertFalse(routingInterceptorWrapped, "should have processed nested routing interceptor in a prior phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test
    fun testInterceptionOrderWhenOuterShouldBeBeforeBecauseOfOrder() = withTestApplication {
        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var userName = ""
        var routingInterceptorWrapped = false

        application.routing {
            install(
                createRouteScopedPlugin("test") {
                    on(PluginsWithProceedHook(ApplicationCallPipeline.Plugins)) {
                        wrappedWithInterceptor = true
                        rootIntercepted = true
                        proceed()
                        wrappedWithInterceptor = false
                    }
                }
            )

            route("user") {
                install(
                    createRouteScopedPlugin("test-route") {
                        onCall {
                            userIntercepted = true
                            routingInterceptorWrapped = wrappedWithInterceptor
                        }
                    }
                )
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(routingInterceptorWrapped, "should have processed nested routing interceptor in an after phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test
    fun testInterceptReceivePipeline() = withTestApplication {
        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var instance: Foo? = null
        var routingInterceptorWrapped = false

        application.routing {
            install(
                createRouteScopedPlugin("test") {
                    on(PluginsWithProceedHook(ApplicationCallPipeline.Plugins)) {
                        wrappedWithInterceptor = true
                        rootIntercepted = true
                        proceed()
                        wrappedWithInterceptor = false
                    }
                }
            )

            route("user") {
                install(
                    createRouteScopedPlugin("test-route") {
                        onCallReceive { _ ->
                            userIntercepted = true
                            routingInterceptorWrapped = wrappedWithInterceptor
                            transformBody { Foo() }
                        }
                    }
                )
                get("{username}") {
                    instance = call.receive()
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(routingInterceptorWrapped, "should have processed nested routing interceptor in an after phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertNotNull(instance)
        }
    }

    @Test
    fun testAcceptHeaderProcessing() = testApplication {
        routing {
            route("/") {
                accept(ContentType.Text.Plain) {
                    handle {
                        call.respond("OK")
                    }
                }
                accept(ContentType.Application.Json) {
                    handle {
                        call.respondText("{\"status\": \"OK\"}", ContentType.Application.Json)
                    }
                }
                accept(ContentType.Application.Xml, ContentType.Text.CSS) {
                    handle {
                        call.respondText("XML or CSS")
                    }
                }
            }
        }

        client.get("/") {
            header(HttpHeaders.Accept, "text/plain")
        }.let {
            assertEquals("OK", it.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Accept, "application/json")
        }.let {
            assertEquals("{\"status\": \"OK\"}", it.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Accept, "application/xml")
        }.let {
            assertEquals("XML or CSS", it.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Accept, "text/css")
        }.let {
            assertEquals("XML or CSS", it.bodyAsText())
        }

        client.get("/").let {
            assertEquals("OK", it.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.Accept, "text/html")
        }.let {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }

        client.get("/") {
            header(HttpHeaders.Accept, "...lla..laa..la")
        }.let {
            assertEquals(HttpStatusCode.BadRequest, it.status)
        }
    }

    @Test
    fun testContentTypeHeaderProcessing() = testApplication {
        routing {
            route("/") {
                contentType(ContentType.Text.Plain) {
                    handle {
                        call.respond("OK")
                    }
                }
                contentType(ContentType.Application.Any) {
                    handle {
                        call.respondText("{\"status\": \"OK\"}", ContentType.Application.Json)
                    }
                }
            }
            route("/nested", HttpMethod.Post) {
                contentType(ContentType.Application.Json) {
                    handle {
                        call.respond("ok")
                    }
                }
            }
            route("/nested", HttpMethod.Get) {
                contentType(ContentType.Application.Json) {
                    handle {
                        call.respond("ok")
                    }
                }
            }
        }

        client.get("/") {
            header(HttpHeaders.ContentType, "text/plain")
        }.let {
            assertEquals("OK", it.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.ContentType, "application/json")
        }.let {
            assertEquals("{\"status\": \"OK\"}", it.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.ContentType, "application/pdf")
        }.let {
            assertEquals("{\"status\": \"OK\"}", it.bodyAsText())
        }

        client.get("/") {
            header(HttpHeaders.ContentType, "text/html")
        }.let {
            assertEquals(HttpStatusCode.UnsupportedMediaType, it.status)
        }

        client.get("/nested") {
            header(HttpHeaders.ContentType, "text/html")
        }.let {
            assertEquals(HttpStatusCode.UnsupportedMediaType, it.status)
        }
    }

    @Test
    fun testTransparentSelectorWithHandler() = withTestApplication {
        application.routing {
            route("") {
                transparent {
                    handle { call.respond("OK") }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("OK", call.response.content)
        }
    }

    @Test
    fun testTransparentSelectorPriority() = withTestApplication {
        application.routing {
            route("root") {
                optionalParam("param") {
                    handle {
                        call.respond("param")
                    }
                }
                transparent {
                    get {
                        call.respond("get")
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/root?param=123").let { call ->
            assertEquals("param", call.response.content)
        }
        handleRequest(HttpMethod.Get, "/root").let { call ->
            assertEquals("get", call.response.content)
        }
    }

    @Test
    fun testHostAndPortRoutingProcessing(): Unit = withTestApplication {
        application.routing {
            route("/") {
                host("my-host", 8080) {
                    get("1") {
                        call.respond(
                            "host = ${call.parameters[HostRouteSelector.HostNameParameter]}, " +
                                "port = ${call.parameters[HostRouteSelector.PortParameter]}"
                        )
                    }
                }
                host("(www\\.)?my-host.net".toRegex()) {
                    get("3") {
                        call.respond(
                            "host = ${call.parameters[HostRouteSelector.HostNameParameter]}, " +
                                "port = ${call.parameters[HostRouteSelector.PortParameter]}"
                        )
                    }
                }
                host(listOf("www.my-host.net", "my-host.net")) {
                    get("4") {
                        call.respond(
                            "host = ${call.parameters[HostRouteSelector.HostNameParameter]}, " +
                                "port = ${call.parameters[HostRouteSelector.PortParameter]}"
                        )
                    }
                }
                port(9090) {
                    get("2") {
                        call.respond(
                            "host = ${call.parameters[HostRouteSelector.HostNameParameter]}, " +
                                "port = ${call.parameters[HostRouteSelector.PortParameter]}"
                        )
                    }
                }
            }
        }

        handleRequest {
            uri = "/1"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host:8080")
        }.let { call ->
            assertEquals("host = my-host, port = 8080", call.response.content)
        }

        handleRequest {
            uri = "/1"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host:80")
        }.let { call ->
            assertEquals(null, call.response.content)
        }

        handleRequest {
            uri = "/2"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host2:9090")
        }.let { call ->
            assertEquals("host = my-host2, port = 9090", call.response.content)
        }

        handleRequest {
            uri = "/3"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host.net:9091")
        }.let { call ->
            assertEquals("host = my-host.net, port = 9091", call.response.content)
        }

        handleRequest {
            uri = "/3"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "www.my-host.net:9092")
        }.let { call ->
            assertEquals("host = www.my-host.net, port = 9092", call.response.content)
        }

        handleRequest {
            uri = "/3"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "sub.my-host.net:9092")
        }.let { call ->
            assertEquals(null, call.response.content)
        }

        handleRequest {
            uri = "/4"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host.net:9093")
        }.let { call ->
            assertEquals("host = my-host.net, port = 9093", call.response.content)
        }

        handleRequest {
            uri = "/4"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "www.my-host.net:9094")
        }.let { call ->
            assertEquals("host = www.my-host.net, port = 9094", call.response.content)
        }
    }

    @Test
    fun testLocalPortRouteProcessing(): Unit = withTestApplication {
        application.routing {
            route("/") {
                // TestApplicationRequest.local defaults to 80 in the absence of headers
                // so connections paths to port 80 in tests should work, whereas other ports shouldn't
                localPort(80) {
                    get("http") {
                        call.respond("received")
                    }
                }
                localPort(443) {
                    get("https") {
                        fail("shouldn't be received")
                    }
                }
            }
        }

        // accepts calls to the specified port
        handleRequest(HttpMethod.Get, "/http").apply {
            assertEquals("received", response.content)
        }

        // ignores calls to different ports
        handleRequest(HttpMethod.Get, "/https").apply {
            assertNull(response.content)
        }

        // I tried to write a test to confirm that it ignores the HTTP Host header,
        // but I couldn't get it to work without adding headers, because
        // [io.ktor.server.testing.TestApplicationRequest.local] is hard-coded to
        // extract the value of those headers.
        // (even though, according to docs, it shouldn't; this should be done by `origin`)

        // I also tried to create a test listening to multiple ports, but I couldn't get it
        // to work because of the same reason above.
    }

    @Test
    fun testRoutingNotCalledForHandledRequests() = testApplication {
        var sideEffect = false
        application {
            val handler = createApplicationPlugin("Handler") {
                onCall { call ->
                    call.respond("plugin")
                }
            }
            install(handler)

            routing {
                get("/") {
                    sideEffect = true
                    call.respond("routing")
                }
            }
        }

        val response = client.get("/").bodyAsText()
        assertEquals("plugin", response)
        assertFalse(sideEffect)
    }

    @Test
    fun testRouteWithParameterPrefixAndSuffixHasMorePriority() = testApplication {
        application {
            routing {
                get("/foo:{baz}") {
                    call.respondText("foo")
                }
                get("/{baz}") {
                    call.respondText("baz")
                }
                get("/{baz}:bar") {
                    call.respondText("bar")
                }
            }
        }

        assertEquals("foo", client.get("/foo:bar").bodyAsText())
        assertEquals("baz", client.get("/baz").bodyAsText())
        assertEquals("bar", client.get("/baz:bar").bodyAsText())
    }

    @Test
    fun testDeepChildComparison() = withTestApplication {
        application.routing {
            header("a", "a") {
                optionalParam("a") {
                    handle {
                        call.respond("a")
                    }
                }
            }
            header("b", "b") {
                param("b") {
                    handle {
                        call.respond("b")
                    }
                }
            }
        }

        // only a match
        handleRequest(HttpMethod.Get, "/") {
            addHeader("a", "a")
            addHeader("b", "b")
        }.let { call ->
            assertEquals("a", call.response.content)
        }

        // only a match
        handleRequest(HttpMethod.Get, "/?a=a") {
            addHeader("a", "a")
            addHeader("b", "b")
        }.let { call ->
            assertEquals("a", call.response.content)
        }

        // both match, b has higher quality
        handleRequest(HttpMethod.Get, "/?b=b") {
            addHeader("a", "a")
            addHeader("b", "b")
        }.let { call ->
            assertEquals("b", call.response.content)
        }

        // both match, same quality
        handleRequest(HttpMethod.Get, "/?a=a&b=b") {
            addHeader("a", "a")
            addHeader("b", "b")
        }.let { call ->
            assertEquals("a", call.response.content)
        }
    }

    @Test
    fun testRoutingErrorStatusCodes() = withTestApplication {
        application.routing {
            route("header_param_method") {
                param("param") {
                    handle {
                        call.respond(HttpStatusCode.OK)
                    }
                }
                header("header", "value") {
                    handle {
                        call.respond(HttpStatusCode.OK)
                    }
                }
                get {
                    call.respond(HttpStatusCode.OK)
                }
            }
            route("method") {
                get {
                    call.respond(HttpStatusCode.OK)
                }
            }
            route("header") {
                header("header", "value") {
                    handle {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            route("param") {
                param("param") {
                    handle {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/method").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }
        handleRequest(HttpMethod.Post, "/method").let { call ->
            assertEquals(HttpStatusCode.MethodNotAllowed, call.response.status())
        }

        handleRequest(HttpMethod.Get, "/param?param=123").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }
        handleRequest(HttpMethod.Get, "/param").let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }

        handleRequest(HttpMethod.Get, "/header") {
            addHeader("header", "value")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }
        handleRequest(HttpMethod.Get, "/header") {
            addHeader("header", "value1")
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }

        handleRequest(HttpMethod.Post, "/header_param_method").let { call ->
            assertEquals(HttpStatusCode.MethodNotAllowed, call.response.status())
        }

        handleRequest(HttpMethod.Get, "/non_existing_path").let { call ->
            assertEquals(HttpStatusCode.NotFound, call.response.status())
        }
    }

    @Test
    fun testRoutingSpecificErrorStatusCodeOnlyWhenPathMatched() = testApplication {
        routing {
            route("a") {
                get {
                    call.respond(HttpStatusCode.OK)
                }
            }
            post {
                call.respond(HttpStatusCode.OK)
            }
        }

        client.post("/a").let { response ->
            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        }
        client.get("/").let { response ->
            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        }
        client.get("/notfound").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun testRoutingSpecificErrorStatusCodeOnlyForConstantQualityPath() = testApplication {
        routing {
            route("a") {
                get {
                    call.respond(HttpStatusCode.OK)
                }
            }
            route("{param}") {
                get {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        client.post("/a").let { response ->
            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        }
        client.post("/b").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun testRoutingSpecificErrorStatusNotForTailcard() = testApplication {
        routing {
            route("a") {
                post {
                    call.respond("a")
                }
            }
            route("{...}") {
                get {
                    call.respond("tailcard")
                }
            }
        }

        client.post("/a").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("a", response.bodyAsText())
        }
        client.get("/a").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("tailcard", response.bodyAsText())
        }
        client.post("/b").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("", response.bodyAsText())
        }
        client.post("/").let { response ->
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("", response.bodyAsText())
        }
    }

    private fun Route.transparent(build: Route.() -> Unit): Route {
        val route = createChild(
            object : RouteSelector() {
                override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
                    return RouteSelectorEvaluation.Success(RouteSelectorEvaluation.qualityTransparent)
                }
            }
        )
        route.build()
        return route
    }
}
