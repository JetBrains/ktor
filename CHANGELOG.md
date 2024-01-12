# 3.0.0-beta-1
> Published 23 November 2023

### Bugfixes
* OkHttp: SSE client throws confusing "Unexpected error" on non 200 status ([KTOR-6390](https://youtrack.jetbrains.com/issue/KTOR-6390))
* Logging plugin blocks response body streaming when level is BODY ([KTOR-6482](https://youtrack.jetbrains.com/issue/KTOR-6482))
* HttpResponseValidator consumes HTTP response body ([KTOR-4225](https://youtrack.jetbrains.com/issue/KTOR-4225))
* CIO: Unable to perform WebSocket upgrade when Content-Type header is sent in the request ([KTOR-6366](https://youtrack.jetbrains.com/issue/KTOR-6366))
* ContentNegotiation: Adding charset to content type of JacksonConverter breaks request matching ([KTOR-6420](https://youtrack.jetbrains.com/issue/KTOR-6420))
* DOS via OOM due to unbound request body size ([KTOR-2682](https://youtrack.jetbrains.com/issue/KTOR-2682))
* AcceptAllCookiesStorage ignores cookie's max-age ([KTOR-2023](https://youtrack.jetbrains.com/issue/KTOR-2023))
* Inconsistent behavior for different engines when exception is thrown in the writer of WriteChannelContent ([KTOR-3266](https://youtrack.jetbrains.com/issue/KTOR-3266))
* Server doesn't send a response when a status code is passed to call.respond and the custom serializer throws an exception ([KTOR-6150](https://youtrack.jetbrains.com/issue/KTOR-6150))
* contentLength() returns null on Android ([KTOR-1540](https://youtrack.jetbrains.com/issue/KTOR-1540))

### Improvements
* Kotlin/JS: Allow passing custom Agent ([KTOR-5861](https://youtrack.jetbrains.com/issue/KTOR-5861))
* Update Kotlin to 1.9.0 ([KTOR-6123](https://youtrack.jetbrains.com/issue/KTOR-6123))
* Update Kotlin to 1.9.20 ([KTOR-6447](https://youtrack.jetbrains.com/issue/KTOR-6447))
* Deprecate Locations with Level.ERROR ([KTOR-6029](https://youtrack.jetbrains.com/issue/KTOR-6029))
* HSTS plugin hard codes port 443 ([KTOR-4168](https://youtrack.jetbrains.com/issue/KTOR-4168))
* API to use java.nio.Path as resources ([KTOR-4275](https://youtrack.jetbrains.com/issue/KTOR-4275))
* Ability to serve static resources from a .zip file ([KTOR-6385](https://youtrack.jetbrains.com/issue/KTOR-6385))
* Make DefaultHeaders plugin Kotlin native compatible ([KTOR-6356](https://youtrack.jetbrains.com/issue/KTOR-6356))
* Disable compression for SSE requests ([KTOR-6327](https://youtrack.jetbrains.com/issue/KTOR-6327))
* Drop http timeout for sse requests ([KTOR-6312](https://youtrack.jetbrains.com/issue/KTOR-6312))
* Add deprecations for old IO API ([KTOR-6036](https://youtrack.jetbrains.com/issue/KTOR-6036))
* Drop old deprecations ([KTOR-6262](https://youtrack.jetbrains.com/issue/KTOR-6262))
* Drop actual modifier for Memory class in jvm for compatibility with K2 ([KTOR-6006](https://youtrack.jetbrains.com/issue/KTOR-6006))
* Remove @Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS") in preparation for Kotlin 2.0 ([KTOR-5824](https://youtrack.jetbrains.com/issue/KTOR-5824))
* Migrate client plugins to new API ([KTOR-6303](https://youtrack.jetbrains.com/issue/KTOR-6303))
* Jetty Server no idle timeout configuration possible ([KTOR-6288](https://youtrack.jetbrains.com/issue/KTOR-6288))
* AttributeKey equality comparison breaks type safety ([KTOR-6122](https://youtrack.jetbrains.com/issue/KTOR-6122))
* Darwin: Support accessing NSURLSessionDelegate ([KTOR-5688](https://youtrack.jetbrains.com/issue/KTOR-5688))
* Remove writing multipart data to temp file ([KTOR-5881](https://youtrack.jetbrains.com/issue/KTOR-5881))
* Netty: Allow listening only for HTTP/1.1 protocol SSL connections ([KTOR-6098](https://youtrack.jetbrains.com/issue/KTOR-6098))
* Persistent Cookie Storage: Make Cookie.matches and Cookie.fillDefaults methods public ([KTOR-6119](https://youtrack.jetbrains.com/issue/KTOR-6119))
* Decompressed response/request should unset Content-Encoding header ([KTOR-6080](https://youtrack.jetbrains.com/issue/KTOR-6080))
* renderSetCookieHeader shouldn't ignore maxAge = 0 ([KTOR-6007](https://youtrack.jetbrains.com/issue/KTOR-6007))
* Webjars plugin should include caching headers and ETag by default ([KTOR-6073](https://youtrack.jetbrains.com/issue/KTOR-6073))
* SessionsConfig.cookie problem with reified type argument ([KTOR-5905](https://youtrack.jetbrains.com/issue/KTOR-5905))
* Make LDAP Auth return generic Principal instead of UserIdPrincipal ([KTOR-793](https://youtrack.jetbrains.com/issue/KTOR-793))
* Logger name should be prefixed with io.ktor ([KTOR-938](https://youtrack.jetbrains.com/issue/KTOR-938))

### Features
* Add Server-sent events (SSE) plugin for client and support for OkHttp engine ([KTOR-505](https://youtrack.jetbrains.com/issue/KTOR-505))
* Client support for SSE plugin (CIO, Apache, Java) ([KTOR-5963](https://youtrack.jetbrains.com/issue/KTOR-5963))
* SSE plugin support in Js, Android, Curl, Darwin, WinHttp client engine ([KTOR-6217](https://youtrack.jetbrains.com/issue/KTOR-6217))
* Add Server-sent events (SSE) plugin for server ([KTOR-6172](https://youtrack.jetbrains.com/issue/KTOR-6172))
* Support UTF-8 BOM character ([KTOR-5812](https://youtrack.jetbrains.com/issue/KTOR-5812))
* Support Compression of Request Body with ContentEncoding Client Plugin ([KTOR-4502](https://youtrack.jetbrains.com/issue/KTOR-4502))

# 2.3.6
> Published 7 November 2023

### Bugfixes
* Resolved connectors job does not complete in TestApplicationEngine ([KTOR-6411](https://youtrack.jetbrains.com/issue/KTOR-6411))
* Darwin: Even a coroutine Job is canceled network load keeps high ([KTOR-6243](https://youtrack.jetbrains.com/issue/KTOR-6243))
* Darwin: EOFException when sending multipart data using Ktor 2.3.4 ([KTOR-6281](https://youtrack.jetbrains.com/issue/KTOR-6281))
* Ktor JS client unconfigurable logging in node ([KTOR-6275](https://youtrack.jetbrains.com/issue/KTOR-6275))
* CIO: getEngineHeaderValues() returns duplicated values ([KTOR-6352](https://youtrack.jetbrains.com/issue/KTOR-6352))
* "Server sent a subprotocol but none was requested" when using Node WebSockets ([KTOR-4001](https://youtrack.jetbrains.com/issue/KTOR-4001))
* YAML properties with literal value null cannot be read since 2.3.1 ([KTOR-6357](https://youtrack.jetbrains.com/issue/KTOR-6357))
* AndroidClientEngine cannot handle content length that exceeds Int range ([KTOR-6344](https://youtrack.jetbrains.com/issue/KTOR-6344))
* Client unable to make subsequent requests after the network disconnection and connection when ResponseObserver is installed ([KTOR-6252](https://youtrack.jetbrains.com/issue/KTOR-6252))
* Outdated Gradle jib plubin does not support application/vnd.oci.image.index.v1+json media type ([KTOR-6280](https://youtrack.jetbrains.com/issue/KTOR-6280))
* KTor 2.3.5 Kotlin 1.9.x upgrade is a breaking change ([KTOR-6354](https://youtrack.jetbrains.com/issue/KTOR-6354))
* WebSockets (CIO): Connection Failure Due to Lowercase 'upgrade' in 'Connection: upgrade' Header ([KTOR-6388](https://youtrack.jetbrains.com/issue/KTOR-6388))
* WinHttp: ArrayIndexOutOfBoundsException when sending WS frame with empty body ([KTOR-6394](https://youtrack.jetbrains.com/issue/KTOR-6394))
* Update dependency com.auth0:jwks-rsa to v0.22.1

# 2.3.5
> Published 5 October 2023

### Bugfixes
* 300+ ktor-client-java threads eat up lots of memory ([KTOR-6292](https://youtrack.jetbrains.com/issue/KTOR-6292))
* Apache5 engine limits concurrent requests to individual route to 5 ([KTOR-6221](https://youtrack.jetbrains.com/issue/KTOR-6221))
* DarwinClientEngine WebSocket rejects all received pongs ([KTOR-5540](https://youtrack.jetbrains.com/issue/KTOR-5540))

# 2.3.4
> Published 31 August 2023

### Bugfixes
* The "charset=UTF-8" part is automatically added to the `application/json` Content-Type ([KTOR-6183](https://youtrack.jetbrains.com/issue/KTOR-6183))
* MicrometerMetricsConfig default registry leaks coroutine ([KTOR-6178](https://youtrack.jetbrains.com/issue/KTOR-6178))
* Darwin: App hangs when sending a huge MultiPart request without access to network ([KTOR-6147](https://youtrack.jetbrains.com/issue/KTOR-6147))
* NPE in JavaClientEngine body() call ([KTOR-6190](https://youtrack.jetbrains.com/issue/KTOR-6190))
* Digest Auth: algorithm isn't specified in the Authorization header ([KTOR-3391](https://youtrack.jetbrains.com/issue/KTOR-3391))
* Confusing NoTransformationFoundException ([KTOR-6064](https://youtrack.jetbrains.com/issue/KTOR-6064))
* Cookie name-value pairs should be separated by a semicolon instead of a comma ([KTOR-5868](https://youtrack.jetbrains.com/issue/KTOR-5868))

# 2.3.3
> Published 1 August 2023

### Bugfixes
* java.util.zip.DataFormatException after enabling permessage-deflate ([KTOR-5979](https://youtrack.jetbrains.com/issue/KTOR-5979))
* DelegatingTestingClientEngine fails when ContentNegotiation with protobuf is installed and empty body ([KTOR-6125](https://youtrack.jetbrains.com/issue/KTOR-6125))
* KtorServlet does not support yaml configuration ([KTOR-6108](https://youtrack.jetbrains.com/issue/KTOR-6108))
* CIO ConnectionFactory leaks on cancellation ([KTOR-6127](https://youtrack.jetbrains.com/issue/KTOR-6127))
* staticFiles responds twice if both index and defaultPath are set ([KTOR-6120](https://youtrack.jetbrains.com/issue/KTOR-6120))
* Uncaught Kotlin exception: kotlin.IllegalArgumentException: Failed to open iconv for charset UTF-8 with error code 22 ([KTOR-5980](https://youtrack.jetbrains.com/issue/KTOR-5980))
* Not compatible with kotlinx-html 0.9.1 ([KTOR-6124](https://youtrack.jetbrains.com/issue/KTOR-6124))
* "Test engine is already completed" error while establishing Websockets connection ([KTOR-6057](https://youtrack.jetbrains.com/issue/KTOR-6057))
* s-maxage is not used, even if `HttpCache.Config.isShared` is true ([KTOR-6087](https://youtrack.jetbrains.com/issue/KTOR-6087))
* Cache returns null when vary header set different ways whatever it has same values ([KTOR-6081](https://youtrack.jetbrains.com/issue/KTOR-6081))
* DefaultRequest: a cookie appears twice in the request header when sending a request with another cookie ([KTOR-5619](https://youtrack.jetbrains.com/issue/KTOR-5619))

### Improvements
* Drop linuxArm64 publication from ktor-client-curl ([KTOR-6154](https://youtrack.jetbrains.com/issue/KTOR-6154))
* Client: Target linuxArm64 ([KTOR-872](https://youtrack.jetbrains.com/issue/KTOR-872))
* Server: Target linuxArm64 ([KTOR-5753](https://youtrack.jetbrains.com/issue/KTOR-5753))
* Add system property to disable automatic installation of runtime shutdown hook ([KTOR-6070](https://youtrack.jetbrains.com/issue/KTOR-6070))

# 2.3.2
> Published 28 June 2023

### Bugfixes
* Linking release build leads to compilation error with coroutines of version 1.7.0-Beta ([KTOR-5728](https://youtrack.jetbrains.com/issue/KTOR-5728))
* MapApplicationConfig removes deeply nested properties when converting to a map ([KTOR-6013](https://youtrack.jetbrains.com/issue/KTOR-6013))
* Cache returns null when vary header has more fields in the cached response ([KTOR-6001](https://youtrack.jetbrains.com/issue/KTOR-6001))
* ContentType of a response body isn't set inside OkHttp's interceptor when a form request is sent ([KTOR-5971](https://youtrack.jetbrains.com/issue/KTOR-5971))

### Improvements
* Update Kotlin to 1.8.22 ([KTOR-6053](https://youtrack.jetbrains.com/issue/KTOR-6053))
* The error message is not helpful when authenticating with a bearer header with a colon ([KTOR-5409](https://youtrack.jetbrains.com/issue/KTOR-5409))

# 2.3.1
> Published 31 May 2023

### Bugfixes
* AndroidClientEngine: the engine double-parses query parameters before sending a request ([KTOR-5814](https://youtrack.jetbrains.com/issue/KTOR-5814))
* Flaky tests in WinHttp engine ([KTOR-5946](https://youtrack.jetbrains.com/issue/KTOR-5946))
* Electron/Node.js detection doesn't work correctly ([KTOR-5906](https://youtrack.jetbrains.com/issue/KTOR-5906))
* Curl sometimes fails with `API function called from within callback` ([KTOR-5918](https://youtrack.jetbrains.com/issue/KTOR-5918))
* Bearer auth token refresh hangs after prior refresh threw an exception ([KTOR-5879](https://youtrack.jetbrains.com/issue/KTOR-5879))
* HOCON: "No configuration setting found for key" error after merging ([KTOR-5895](https://youtrack.jetbrains.com/issue/KTOR-5895))
* Ktor Client Unable to Stream Responses in Javascript ([KTOR-5867](https://youtrack.jetbrains.com/issue/KTOR-5867))
* Darwin engine does not support streaming of request body ([KTOR-5899](https://youtrack.jetbrains.com/issue/KTOR-5899))
* The Logging plugin doesn't log full kotlinx deserialization errors ([KTOR-5421](https://youtrack.jetbrains.com/issue/KTOR-5421))
* XForwardedHeaders should set `remoteAddress` in addition to `remoteHost` ([KTOR-5786](https://youtrack.jetbrains.com/issue/KTOR-5786))
* Sessions: Set-Cookie is added on every api request ([KTOR-912](https://youtrack.jetbrains.com/issue/KTOR-912))
* RateLimitters for every request key live in memory forever ([KTOR-5872](https://youtrack.jetbrains.com/issue/KTOR-5872))
* Significant delay between getting a part and starting reading from its provider for multipart/form-data requests ([KTOR-5248](https://youtrack.jetbrains.com/issue/KTOR-5248))
* getTimeMillis has seconds precision on native ([KTOR-5878](https://youtrack.jetbrains.com/issue/KTOR-5878))
* A coroutine closed due to cancellation is considered by the JsWebSocketSession to be closed on failure ([KTOR-2932](https://youtrack.jetbrains.com/issue/KTOR-2932))
* WebSockets: requests to a non-existing route cause server to lock up after responding with 404 (potential DOS) ([KTOR-5829](https://youtrack.jetbrains.com/issue/KTOR-5829))
* testApplication: NPE when test server doesn't reply with an HTTP upgrade ([KTOR-5815](https://youtrack.jetbrains.com/issue/KTOR-5815))
* GMTDate timestamp doesn't reflect timezone when created using `Calendar.toDate` method ([KTOR-5813](https://youtrack.jetbrains.com/issue/KTOR-5813))

### Improvements
* Warn when the RateLimit plugin installed after the routing ([KTOR-5915](https://youtrack.jetbrains.com/issue/KTOR-5915))
* Allow access to RateLimiters related to call ([KTOR-5876](https://youtrack.jetbrains.com/issue/KTOR-5876))
* Multipart: Support not writing a temporary file for binary data ([KTOR-5864](https://youtrack.jetbrains.com/issue/KTOR-5864))
* Make System Property to Set outgoingToBeProcessed Size for WebSockets ([KTOR-5855](https://youtrack.jetbrains.com/issue/KTOR-5855))
* Support optional properties in YAML ([KTOR-5796](https://youtrack.jetbrains.com/issue/KTOR-5796))
* YAML config does not support reading variables from itself ([KTOR-5797](https://youtrack.jetbrains.com/issue/KTOR-5797))

# 2.3.0
> Published 19 April 2023

### Features
* Support loading multiple configuration files ([KTOR-5658](https://youtrack.jetbrains.com/issue/KTOR-5658))
* Static files filters or something similar to mod_rewrite ([KTOR-818](https://youtrack.jetbrains.com/issue/KTOR-818))
* Built-in support for HEAD requests for static files ([KTOR-4052](https://youtrack.jetbrains.com/issue/KTOR-4052))
* Ability to set Content-Type of static resource ([KTOR-2312](https://youtrack.jetbrains.com/issue/KTOR-2312))
* Support regex patterns in routing ([KTOR-5110](https://youtrack.jetbrains.com/issue/KTOR-5110))
* Support Flow in ktor-serialization ([KTOR-3788](https://youtrack.jetbrains.com/issue/KTOR-3788))
* Upgrade Client Apache Engine Version to use Apache 5 ([KTOR-4547](https://youtrack.jetbrains.com/issue/KTOR-4547))
* Support for CURLOPT_CAINFO and CURLOPT_CAPATH in ktor-client-curl ([KTOR-5614](https://youtrack.jetbrains.com/issue/KTOR-5614))
* Allow passing multiple acceptable content types to accept route selector ([KTOR-419](https://youtrack.jetbrains.com/issue/KTOR-419))
* Support `100 Continue` ([KTOR-829](https://youtrack.jetbrains.com/issue/KTOR-829))

### Improvements
* The '425 Too Early' status code is missing in the HttpStatusCode enum ([KTOR-4673](https://youtrack.jetbrains.com/issue/KTOR-4673))
* Feature request: SO_REUSEADDR option for embedded server ([KTOR-5529](https://youtrack.jetbrains.com/issue/KTOR-5529))
* Add opportunity to pass type info into WebSockets serializing methods ([KTOR-5740](https://youtrack.jetbrains.com/issue/KTOR-5740))
* Ktor JS websocket client unconfigurable logging ([KTOR-5456](https://youtrack.jetbrains.com/issue/KTOR-5456))
* Update JTE to 2.3.0 ([KTOR-5698](https://youtrack.jetbrains.com/issue/KTOR-5698))
* Update Kotlin to 1.8.10 ([KTOR-5544](https://youtrack.jetbrains.com/issue/KTOR-5544))
* Migrate to the new Kotlin JS IR backend ([KTOR-5543](https://youtrack.jetbrains.com/issue/KTOR-5543))
* Prefer Node instead of browser behavior ([KTOR-5650](https://youtrack.jetbrains.com/issue/KTOR-5650))
* Update reported dependencies ([KTOR-5662](https://youtrack.jetbrains.com/issue/KTOR-5662))
* AutoHead should dispose response body ([KTOR-5684](https://youtrack.jetbrains.com/issue/KTOR-5684))
* Add `append(String, List<String>)` overload to `FormBuilder` ([KTOR-5493](https://youtrack.jetbrains.com/issue/KTOR-5493))
* Support serving static files from resources in GraalVM native image ([KTOR-5580](https://youtrack.jetbrains.com/issue/KTOR-5580))
* Comparable HttpStatusCode ([KTOR-5629](https://youtrack.jetbrains.com/issue/KTOR-5629))
* Support preCompressed with resources ([KTOR-2677](https://youtrack.jetbrains.com/issue/KTOR-2677))
* Add shutdown configuration for engine in stop method ([KTOR-5560](https://youtrack.jetbrains.com/issue/KTOR-5560))
* Logging: Add filter/sanitization of sensitive headers ([KTOR-5523](https://youtrack.jetbrains.com/issue/KTOR-5523))
* Add resource route builders accepting typed body as second parameter ([KTOR-5589](https://youtrack.jetbrains.com/issue/KTOR-5589))
* CallLogging: add config to avoid logging static file request ([KTOR-5474](https://youtrack.jetbrains.com/issue/KTOR-5474))
* Update Tomcat to 10 ([KTOR-5266](https://youtrack.jetbrains.com/issue/KTOR-5266))
* Update Jetty to version 11 ([KTOR-5267](https://youtrack.jetbrains.com/issue/KTOR-5267))
* Update Parameters and Headers DSL to be consistent with stdlib ([KTOR-627](https://youtrack.jetbrains.com/issue/KTOR-627))
* Consider quoting `Boolean` during construction of multipart requests ([KTOR-5405](https://youtrack.jetbrains.com/issue/KTOR-5405))
* Simplify Static Content Plugin ([KTOR-5265](https://youtrack.jetbrains.com/issue/KTOR-5265))

### Bugfixes
* Websockets: connection should be failed immediately when no continuation frame goes after a fragmented text frame ([KTOR-5018](https://youtrack.jetbrains.com/issue/KTOR-5018))
* Websockets: Connection should be failed immediately, since all data frames after the initial data frame must have opcode 0 ([KTOR-5014](https://youtrack.jetbrains.com/issue/KTOR-5014))
* Websockets: Connection should fail immediately (1002/Protocol Error) when control frame has a payload with more than 125 octets ([KTOR-5006](https://youtrack.jetbrains.com/issue/KTOR-5006))
* Java engine: Websockets client sends two PONG frames for each PING frame from a server ([KTOR-5653](https://youtrack.jetbrains.com/issue/KTOR-5653))
* Websockets: Erroneous trace log about expired websocket pings ([KTOR-5672](https://youtrack.jetbrains.com/issue/KTOR-5672))
* DarwinClientEngine: a request deadlocks on macOS since 2.2.2 ([KTOR-5502](https://youtrack.jetbrains.com/issue/KTOR-5502))
* Requests don't match in nested Regex Routing ([KTOR-5750](https://youtrack.jetbrains.com/issue/KTOR-5750))
* IllegalArgumentException in Regex Routing ([KTOR-5748](https://youtrack.jetbrains.com/issue/KTOR-5748))
* Unneeded escaping in Regex Routing isn't processed ([KTOR-5746](https://youtrack.jetbrains.com/issue/KTOR-5746))
* Native: Read from a closed socket doesn't throw an exception ([KTOR-5093](https://youtrack.jetbrains.com/issue/KTOR-5093))
* Reading response of HEAD request breaks when Content-Length > 0 ([KTOR-5699](https://youtrack.jetbrains.com/issue/KTOR-5699))
* "Serializer for class 'Any' is not found" error when responding with Any type since Ktor 2.2.4 ([KTOR-5687](https://youtrack.jetbrains.com/issue/KTOR-5687))
* BearerAuthProvider: Token is being refreshed multiple times when queued call finishes with 401 after refresh token succeeds ([KTOR-5681](https://youtrack.jetbrains.com/issue/KTOR-5681))
* CIO: nmap crashes server with "SocketException: Invalid argument" error ([KTOR-5636](https://youtrack.jetbrains.com/issue/KTOR-5636))
* DigestAuthProvider: realm sent from the server doesn't participate in the computation of `response` ([KTOR-4514](https://youtrack.jetbrains.com/issue/KTOR-4514))
* OAuth2: "JsonObject is not a JsonPrimitive" error when server replies with nested JSON object on a token request ([KTOR-5669](https://youtrack.jetbrains.com/issue/KTOR-5669))
* CallLogging: logs caused by an exception are suppressed when mdc provider is configured ([KTOR-5665](https://youtrack.jetbrains.com/issue/KTOR-5665))
* Metrics: ClassCastException when the DropwizardMetrics plugin is installed after the MicrometerMetrics plugin ([KTOR-5595](https://youtrack.jetbrains.com/issue/KTOR-5595))
* ByteBufferChannel throws exception when HttpCache, ContentEncoding and onDownload are used ([KTOR-5532](https://youtrack.jetbrains.com/issue/KTOR-5532))
* runBlocking in TestApplicationEngine loses coroutineContext ([KTOR-5525](https://youtrack.jetbrains.com/issue/KTOR-5525))
* Incorrect handling of private cache directive in HttpCachePlugin ([KTOR-5570](https://youtrack.jetbrains.com/issue/KTOR-5570))
* The default implementation of challengeFunction is empty, causing no session users to access protected resources ([KTOR-5467](https://youtrack.jetbrains.com/issue/KTOR-5467))
* Wrong ContentType for .mjs files ([KTOR-5533](https://youtrack.jetbrains.com/issue/KTOR-5533))
* Non-standard `Content-Type` headers for static files ([KTOR-5311](https://youtrack.jetbrains.com/issue/KTOR-5311))
* CIO: Client fails to parse response without Content-Length, Connection headers and chunked transfer encoding ([KTOR-5327](https://youtrack.jetbrains.com/issue/KTOR-5327))
* Conflict between `ContentNegotiation` and `Mustache` plugins ([KTOR-5337](https://youtrack.jetbrains.com/issue/KTOR-5337))

# 2.2.4
> Published 28 February 2023

### Bugfixes
* Connect timeout is not respected when using the HttpRequestRetry plugin ([KTOR-5466](https://youtrack.jetbrains.com/issue/KTOR-5466))
* URLs with underscore fail to parse correctly in HTTP client request ([KTOR-5591](https://youtrack.jetbrains.com/issue/KTOR-5591))
* Routing: Wrong content-type results in 405 instead of 415 status code with two routes ([KTOR-5535](https://youtrack.jetbrains.com/issue/KTOR-5535))
* Compressing the response will result in unexpected ERROR log output after processing in the StatusPages ([KTOR-5510](https://youtrack.jetbrains.com/issue/KTOR-5510))
* Javadoc for Resources.kt cannot be compiled ([KTOR-5492](https://youtrack.jetbrains.com/issue/KTOR-5492))
* ContentNegotiation: The "charset=UTF-8" part is added for the Content-Type header ([KTOR-3799](https://youtrack.jetbrains.com/issue/KTOR-3799))
* kotlinx.serialization.SerializationException is lost for the classes that have generic type parameters ([KTOR-5448](https://youtrack.jetbrains.com/issue/KTOR-5448))
* OkHttp: Cancelling while writing to ByteWriteChannel when overriding WriteChannelContent causes propagation of CancellationException to a caller ([KTOR-5518](https://youtrack.jetbrains.com/issue/KTOR-5518))

# 2.2.3
> Published 31 January 2023

### Improvements

* ContentNegotiation: "Skipping because the type is ignored" log message is unclear ([KTOR-5479](https://youtrack.jetbrains.com/issue/KTOR-5479))
* Make OAuth2 functionality multiplatform ([KTOR-1144](https://youtrack.jetbrains.com/issue/KTOR-1144))
* Log HTTP request time ([KTOR-1250](https://youtrack.jetbrains.com/issue/KTOR-1250))
* Add Client Plugins Trace Logging ([KTOR-5264](https://youtrack.jetbrains.com/issue/KTOR-5264))

### Bugfixes

* FileStorage throws java.io.FileNotFoundException (File name too long) when request path is long ([KTOR-5443](https://youtrack.jetbrains.com/issue/KTOR-5443))
* HttpRequestRetry retries on FileNotFoundException thrown by FileStorage ([KTOR-5444](https://youtrack.jetbrains.com/issue/KTOR-5444))
* DropwizardMetricsPlugin logs status code incorrectly when is used together with StatusPages plugin ([KTOR-5420](https://youtrack.jetbrains.com/issue/KTOR-5420))
* Server ContentNegotiation no longer allows multiple decoders for one Content-Type ([KTOR-5410](https://youtrack.jetbrains.com/issue/KTOR-5410))
* Multipart File doesn't upload whole file, throws "Unexpected EOF: expected 4096 more bytes" for larger files ([KTOR-3455](https://youtrack.jetbrains.com/issue/KTOR-3455))
* Netty: Unable to set the `tcpKeepAlive` ([KTOR-5370](https://youtrack.jetbrains.com/issue/KTOR-5370))
* HOCON: CLI parameters don't override custom array properties since 2.1.0 ([KTOR-5100](https://youtrack.jetbrains.com/issue/KTOR-5100))

# 2.2.2
> Published 3 January 2023

### Improvements

* Resource annotation should be MetaSerializable ([KTOR-5397](https://youtrack.jetbrains.com/issue/KTOR-5397))
* The swaggerUI method is too restrictive and cannot be called inside a route ([KTOR-5307](https://youtrack.jetbrains.com/issue/KTOR-5307))
* Engine shutdown grace period and timeout are not configurable ([KTOR-5359](https://youtrack.jetbrains.com/issue/KTOR-5359))
* Allow specifying immutable in CacheControl ([KTOR-3757](https://youtrack.jetbrains.com/issue/KTOR-3757))

### Bugfixes

* Server cannot be started with the Swagger plugin ([KTOR-5308](https://youtrack.jetbrains.com/issue/KTOR-5308))
* Regression in 2.2.1: Got EOF but at least 0 bytes were expected ([5372](https://youtrack.jetbrains.com/issue/KTOR-5372))
* HttpRequestRetry: Memory leak of coroutines objects when using the plugin ([KTOR-5099](https://youtrack.jetbrains.com/issue/KTOR-5099))
* iOS unit test deadlocks with DarwinClientEngine ([KTOR-5332](https://youtrack.jetbrains.com/issue/KTOR-5332))
* Gzip encoding: IllegalStateException: Expected 112, actual 113 ([KTOR-5300](https://youtrack.jetbrains.com/issue/KTOR-5300))
* Netty, HSTS: UnsupportedOperationException is thrown when the server responds before HSTS plugin ([KTOR-5276](https://youtrack.jetbrains.com/issue/KTOR-5276))


# 2.2.1
> Published 7 December 2022

The critical error `java.lang.NoClassDefFoundError: kotlinx/atomicfu/AtomicFU` in the 2.2.0 release is fixed

# 2.2.0
> Published 7 December 2022

* Intergate Swagger UI Hosting as Ktor Feature ([KTOR-774](https://youtrack.jetbrains.com/issue/KTOR-774))
* New plugins API for client ([KTOR-5161](https://youtrack.jetbrains.com/issue/KTOR-5161))
* Rate-Limit Support on Server ([KTOR-1196](https://youtrack.jetbrains.com/issue/KTOR-1196))
* Make sessions plugin multiplatform ([KTOR-4960](https://youtrack.jetbrains.com/issue/KTOR-4960))
* Add the ability to access the route inside a route-scoped plugin ([KTOR-5112](https://youtrack.jetbrains.com/issue/KTOR-5112))
* Add a method that returns a list of child routes recursively ([KTOR-581](https://youtrack.jetbrains.com/issue/KTOR-581))
* Support Default Value for missing Env Variables in YAML ([KTOR-5283](https://youtrack.jetbrains.com/issue/KTOR-5283))
* Netty: ApplicationStarted event is fired before the server starts accepting connections ([KTOR-4259](https://youtrack.jetbrains.com/issue/KTOR-4259))
* parseAuthorizationHeader throws ParseException on header value with multiple challenges ([KTOR-5216](https://youtrack.jetbrains.com/issue/KTOR-5216))
* ByteChannel exception: Got EOF but at least 1 byte were expected ([KTOR-5252](https://youtrack.jetbrains.com/issue/KTOR-5252))
* Application data in OAuth State parameter ([KTOR-5225](https://youtrack.jetbrains.com/issue/KTOR-5225))
* NativePRNGNonBlocking is not found, fallback to SHA1PRNG ([KTOR-668](https://youtrack.jetbrains.com/issue/KTOR-668))
* Not calling call.respond() at server results in 404 for the client ([KTOR-721](https://youtrack.jetbrains.com/issue/KTOR-721))
* Restoring thread context elements when directly resuming to parent is broken ([KTOR-2644](https://youtrack.jetbrains.com/issue/KTOR-2644))
* Out of the box ContentConverter for Protobuf ([KTOR-763](https://youtrack.jetbrains.com/issue/KTOR-763))
* Darwin: response is never returned when usePreconfiguredSession is used ([KTOR-5134](https://youtrack.jetbrains.com/issue/KTOR-5134))
* List<ApplicationConfig>.merge() should have reversed priority ([KTOR-5208](https://youtrack.jetbrains.com/issue/KTOR-5208))
* Allow nested authentications to be combined using AND ([KTOR-5021](https://youtrack.jetbrains.com/issue/KTOR-5021))
* The swaggerUI plugin should be placed in the io.ktor.server.plugins.swagger package ([KTOR-5192](https://youtrack.jetbrains.com/issue/KTOR-5192))
* CORS Plugin should log reason for returning 403 Forbidden errors ([KTOR-4236](https://youtrack.jetbrains.com/issue/KTOR-4236))
* The default path to an OpenAPI specification doesn't work for the 'openAPI' plugin ([KTOR-5193](https://youtrack.jetbrains.com/issue/KTOR-5193))
* JWT: JWTPayloadHolder.getListClaim() throws NPE when specified claim is absent ([KTOR-5098](https://youtrack.jetbrains.com/issue/KTOR-5098))
* Logging: the plugin instantiates the default logger even when a custom one is provided ([KTOR-5186](https://youtrack.jetbrains.com/issue/KTOR-5186))
* Java client engine doesn't handle HttpTimeout.INFINITE_TIMEOUT_MS properly ([KTOR-2814](https://youtrack.jetbrains.com/issue/KTOR-2814))
* SessionTransportTransformerMessageAuthentication: Comparison of digests fails when there is a space in a value ([KTOR-5168](https://youtrack.jetbrains.com/issue/KTOR-5168))
* Support serving OpenAPI from resources ([KTOR-5150](https://youtrack.jetbrains.com/issue/KTOR-5150))
* Remove check for internal class in Select ([KTOR-5035](https://youtrack.jetbrains.com/issue/KTOR-5035))
* Persistent Client HttpCache ([KTOR-2579](https://youtrack.jetbrains.com/issue/KTOR-2579))
* Support native windows HTTP client ([KTOR-735](https://youtrack.jetbrains.com/issue/KTOR-735))
* Add Server BearerAuthenticationProvider ([KTOR-5118](https://youtrack.jetbrains.com/issue/KTOR-5118))
* Merged config: "Property *.size not found" error when calling `configList` method on an array property ([KTOR-5143](https://youtrack.jetbrains.com/issue/KTOR-5143))
* "POSIX error 56: Socket is already connected" error when a socket is connection-mode on Darwin targets ([KTOR-4877](https://youtrack.jetbrains.com/issue/KTOR-4877))
* StatusPages can't handle errors in HTML template ([KTOR-5107](https://youtrack.jetbrains.com/issue/KTOR-5107))
* HttpRequestRetry: Memory leak of coroutines objects when using the plugin ([KTOR-5099](https://youtrack.jetbrains.com/issue/KTOR-5099))
* CallLogging and CallId: exceptions thrown in WriteChannelContent.writeTo are swallowed ([KTOR-4954](https://youtrack.jetbrains.com/issue/KTOR-4954))
* Temp files generated by multipart upload are not cleared in case of exception or cancellation ([KTOR-5051](https://youtrack.jetbrains.com/issue/KTOR-5051))
* Websockets, Darwin: trusting a certificate via `handleChallenge` doesn't work for Websockets connections ([KTOR-5094](https://youtrack.jetbrains.com/issue/KTOR-5094))
* Digest auth: Support returning any objects which implement Principal interface ([KTOR-5059](https://youtrack.jetbrains.com/issue/KTOR-5059))
* Add Debug Logging to Default Transformers ([KTOR-4529](https://youtrack.jetbrains.com/issue/KTOR-4529))
* No way getting client's source address from IP packet ([KTOR-2501](https://youtrack.jetbrains.com/issue/KTOR-2501))
* Add Env Variable to Change Log Level on Native Server ([KTOR-4998](https://youtrack.jetbrains.com/issue/KTOR-4998))
* Add Debug Logging for Ktor Plugins and Routing ([KTOR-4510](https://youtrack.jetbrains.com/issue/KTOR-4510))
* Add Debug Logging to ContentNegotiation ([KTOR-4518](https://youtrack.jetbrains.com/issue/KTOR-4518))
* Add Debug Logging to Routing ([KTOR-4524](https://youtrack.jetbrains.com/issue/KTOR-4524))
* Add Debug Logging to Auth Plugin ([KTOR-4519](https://youtrack.jetbrains.com/issue/KTOR-4519))
* Add Debug Logging to Status Pages Plugin ([KTOR-4527](https://youtrack.jetbrains.com/issue/KTOR-4527))
* Add Debug Logging to PartialContent Plugin ([KTOR-4525](https://youtrack.jetbrains.com/issue/KTOR-4525))
* Add Debug Logging to Sessions Plugin ([KTOR-4526](https://youtrack.jetbrains.com/issue/KTOR-4526))
* Add Debug Logging to Call Id ([KTOR-4520](https://youtrack.jetbrains.com/issue/KTOR-4520))
* Add Debug Logging to WebSockets Plugin ([KTOR-4528](https://youtrack.jetbrains.com/issue/KTOR-4528))
* Add Debug Logging to Double Receive Plugin ([KTOR-4530](https://youtrack.jetbrains.com/issue/KTOR-4530))
* Add Debug Logging to Compression Plugin ([KTOR-4521](https://youtrack.jetbrains.com/issue/KTOR-4521))
* Make certificate generation helpers more flexible ([KTOR-5023](https://youtrack.jetbrains.com/issue/KTOR-5023))
* Jackson converter: Support requests with Content-Length header ([KTOR-4904](https://youtrack.jetbrains.com/issue/KTOR-4904))
* Add a way to get a client's port ([KTOR-430](https://youtrack.jetbrains.com/issue/KTOR-430))
* Retry and timeout client plugins don't work together ([KTOR-4652](https://youtrack.jetbrains.com/issue/KTOR-4652))
* Server Session - Switch to Kotlinx serialization ([KTOR-2572](https://youtrack.jetbrains.com/issue/KTOR-2572))
* ApplicationCall.respondRedirect should have overload for Url ([KTOR-1538](https://youtrack.jetbrains.com/issue/KTOR-1538))
* Make API to Use Configuration in Application Plugins ([KTOR-4533](https://youtrack.jetbrains.com/issue/KTOR-4533))
* Way to block use of TLS 1.0/1.1 when using Ktor/Netty ([KTOR-4587](https://youtrack.jetbrains.com/issue/KTOR-4587))
* testApplication: application initialization block isn't eagerly called ([KTOR-4819](https://youtrack.jetbrains.com/issue/KTOR-4819))
* testApplication: test server lifecycle management ([KTOR-4773](https://youtrack.jetbrains.com/issue/KTOR-4773))
* The beginning character of encodedPath field(Url class) is wrong when relative path ([KTOR-621](https://youtrack.jetbrains.com/issue/KTOR-621))
* Unable to access userPrincipal of servletRequest in ktor-server-servlet ([KTOR-4784](https://youtrack.jetbrains.com/issue/KTOR-4784))
* When unable to get JWKS, JWTAuth swallows the underlying exception and only logs the last message ([KTOR-636](https://youtrack.jetbrains.com/issue/KTOR-636))
* CIO Server generates wrong URL for OAuth URL provider using Locations ([KTOR-2143](https://youtrack.jetbrains.com/issue/KTOR-2143))
* Inconsistency among server engines when determining port/host of an incoming request ([KTOR-4141](https://youtrack.jetbrains.com/issue/KTOR-4141))
* Update Versions of Dependencies ([KTOR-5293](https://youtrack.jetbrains.com/issue/KTOR-5293))

# 2.1.3
> Published 26 October 2022

* JS: window.location.origin returns null when executed in iframe via srcdoc attribute ([KTOR-4993](https://youtrack.jetbrains.com/issue/KTOR-4993))
* SensitivityWatchEventModifier - Move the reflection call of this modifier out from the Ktor Core ([KTOR-1647](https://youtrack.jetbrains.com/issue/KTOR-1647))
* "java.lang.IllegalArgumentException: Failed requirement." in SelectorManagerSupport ([KTOR-2914](https://youtrack.jetbrains.com/issue/KTOR-2914))
* HOCON: CLI parameters don't override custom properties since 2.1.0 ([KTOR-5000](https://youtrack.jetbrains.com/issue/KTOR-5000))
* Websockets timeout doesn't cause a close of a connection ([KTOR-3504](https://youtrack.jetbrains.com/issue/KTOR-3504))
* DefaultHeaders: a header is duplicated in a StatusPages's handler ([KTOR-4990](https://youtrack.jetbrains.com/issue/KTOR-4990))
* Websockets: timeout doesn't cause closing of incoming and outgoing channels ([KTOR-2430](https://youtrack.jetbrains.com/issue/KTOR-2430))
* RFC 3986 recommendation for encoding URI is NOT followed ([KTOR-993](https://youtrack.jetbrains.com/issue/KTOR-993))
* Cookies: Invalid encoding of cookies' values since 1.4.0 ([KTOR-917](https://youtrack.jetbrains.com/issue/KTOR-917))
* ByteReadChannel is unable to read files with long lines ([KTOR-2588](https://youtrack.jetbrains.com/issue/KTOR-2588))
* WebSocketDeflateExtension configureProtocols always failed with stackOverflow ([KTOR-4916](https://youtrack.jetbrains.com/issue/KTOR-4916))
* Update Kotlin to 1.7.20 ([KTOR-4963](https://youtrack.jetbrains.com/issue/KTOR-4963))
* Netty HTTP/2: response headers contain ":status" header and that leads to IllegalHeaderNameException in the ConditionalHeaders plugin ([KTOR-4943](https://youtrack.jetbrains.com/issue/KTOR-4943))
* Maven: ktor-server-test-host-jvm causes dependency error starting from Ktor 2.0.3 ([KTOR-4900](https://youtrack.jetbrains.com/issue/KTOR-4900))
* Autoreloading: "Flow invariant is violated" error since Ktor 2.0.3 ([KTOR-4926](https://youtrack.jetbrains.com/issue/KTOR-4926))
* Autoreloading: ClassCastException when retrieving plugins in testApplication ([KTOR-4729](https://youtrack.jetbrains.com/issue/KTOR-4729))
* CIO engine has wrong doc for request timeout ([KTOR-4941](https://youtrack.jetbrains.com/issue/KTOR-4941))
* CIO: A request through a proxy server results in 403 from Cloudflare ([KTOR-4925](https://youtrack.jetbrains.com/issue/KTOR-4925))

# 2.1.2
> Published 29 September 2022

* HttpCacheEntry ignoring Request Cache-Control directives ([KTOR-4894](https://youtrack.jetbrains.com/issue/KTOR-4894))
* testApplication does not handle port and connectors ([KTOR-4875](https://youtrack.jetbrains.com/issue/KTOR-4875))
* Native: Wrong status code when requesting with DELETE method and body ([KTOR-3566](https://youtrack.jetbrains.com/issue/KTOR-3566))
* Default host address 0.0.0.0 isn't reachable on Windows ([KTOR-4834](https://youtrack.jetbrains.com/issue/KTOR-4834))
* TestApplicationEngine error handling is inconsistent with DefaultEnginePipeline, breaking clients ([KTOR-4009](https://youtrack.jetbrains.com/issue/KTOR-4009))
* Routing: Wrong content-type results in 400 Bad Request instead of 415 Unsupported Media type ([KTOR-4849](https://youtrack.jetbrains.com/issue/KTOR-4849))

# 2.1.1
> Published 6 September 2022

* CIO: responses are received with a huge delay on JVM Windows (due to reverse DNS lookup internally) ([KTOR-4827](https://youtrack.jetbrains.com/issue/KTOR-4827))
* Netty HTTP/2 not working ([KTOR-578](https://youtrack.jetbrains.com/issue/KTOR-578))
* HTTP/2 push fails with Netty engine ([KTOR-800](https://youtrack.jetbrains.com/issue/KTOR-800))
* HttpCookies: no space between cookie pairs ([KTOR-3854](https://youtrack.jetbrains.com/issue/KTOR-3854))
* Netty ALPN provider detection not working ([KTOR-4712](https://youtrack.jetbrains.com/issue/KTOR-4712))
* CIO: Connection reset by peer on MacOS ([KTOR-2036](https://youtrack.jetbrains.com/issue/KTOR-2036))
* CallLogging MDC with sessions: Application feature Sessions is not installed ([KTOR-550](https://youtrack.jetbrains.com/issue/KTOR-550))
* Deprecate Public API with Atomicfu Declarations ([KTOR-4774](https://youtrack.jetbrains.com/issue/KTOR-4774))
* Deprecate receiveOrNull because it's confusing ([KTOR-4772](https://youtrack.jetbrains.com/issue/KTOR-4772))
* Server ContentNegotiation Plugin doesn't check ignoredTypes for Request Body ([KTOR-4770](https://youtrack.jetbrains.com/issue/KTOR-4770))
* IllegalArgumentException is thrown when UnixSocketAddress.path is accessed on JVM (JDK 16+) ([KTOR-4695](https://youtrack.jetbrains.com/issue/KTOR-4695))
* WebSocketDeflateExtension not following RFC ([KTOR-4696](https://youtrack.jetbrains.com/issue/KTOR-4696))
* The parseWebSocketExtensions function behaves incorrectly ([KTOR-3189](https://youtrack.jetbrains.com/issue/KTOR-3189))
* Receive non-Nullable Type Throws NPE in Case of Failure ([KTOR-4771](https://youtrack.jetbrains.com/issue/KTOR-4771))
* Darwin: Symbol not found: _OBJC_CLASS_$_NSURLSessionWebSocketMessage on iOS 12 ([KTOR-4159](https://youtrack.jetbrains.com/issue/KTOR-4159))
* Fix Merging Date Headers on the Client ([KTOR-4782](https://youtrack.jetbrains.com/issue/KTOR-4782))
* Replace exception in InputStreamAdapter and OutputStreamAdapter constructors with warning message If parking ([KTOR-4736](https://youtrack.jetbrains.com/issue/KTOR-4736))
* Clearing Session Cookie in Chrome 80+ with SameSite and Secure ([KTOR-437](https://youtrack.jetbrains.com/issue/KTOR-437))
* The `OutgoingContent.toByteArray()` stalls when used in combination with a `OutgoingContent.WriteChannelContent` ([KTOR-2126](https://youtrack.jetbrains.com/issue/KTOR-2126))
* Missing Content-Type header in a request ([KTOR-1407](https://youtrack.jetbrains.com/issue/KTOR-1407))
* Crash when making requests from browser inside of web worker ([KTOR-4715](https://youtrack.jetbrains.com/issue/KTOR-4715))
* An error occurs when there is a binary such as protobuf in the response body of error ([KTOR-1619](https://youtrack.jetbrains.com/issue/KTOR-1619))
* CallLogging configured MDC entries are not passed to StatusPages exception handlers ([KTOR-4193](https://youtrack.jetbrains.com/issue/KTOR-4193))
* LocalFileContent incorrectly relies on the last modification time of a file to check its existence ([KTOR-4707](https://youtrack.jetbrains.com/issue/KTOR-4707))
* Sessions: WSS in combination with Secure cookies throws IllegalArgumentException ([KTOR-4697](https://youtrack.jetbrains.com/issue/KTOR-4697))
* Json request failure with configured form authentication ([KTOR-678](https://youtrack.jetbrains.com/issue/KTOR-678))

# 2.1.0
> Published 11 August 2022

* Add YAML Configuration Format Support ([KTOR-3572](https://youtrack.jetbrains.com/issue/KTOR-3572))
* Allow overriding HSTS settings per host ([KTOR-4578](https://youtrack.jetbrains.com/issue/KTOR-4578))
* CORS: Pattern matching for origin ([KTOR-316](https://youtrack.jetbrains.com/issue/KTOR-316))
* Darwin: Allow setting custom NSURLSession ([KTOR-583](https://youtrack.jetbrains.com/issue/KTOR-583))
* Support setting caching options on call ([KTOR-457](https://youtrack.jetbrains.com/issue/KTOR-457))
* Revert default behavior of string encoding for ContentNegotiation and JsonPlugin ([KTOR-4739](https://youtrack.jetbrains.com/issue/KTOR-4739))
* Make Content-Length header validation optional ([KTOR-4655](https://youtrack.jetbrains.com/issue/KTOR-4655))
* Client resources plugin miss builders for PATCH method ([KTOR-4658](https://youtrack.jetbrains.com/issue/KTOR-4658))
* The awaitSuspend method wakes up early in closed ByteChannelSequential ([KTOR-4597](https://youtrack.jetbrains.com/issue/KTOR-4597))
* HttpCache plugin does not support max-stale directive ([KTOR-4610](https://youtrack.jetbrains.com/issue/KTOR-4610))
* Incoming request body validation ([KTOR-503](https://youtrack.jetbrains.com/issue/KTOR-503))
* Client does not support sending or receiving json null value ([KTOR-745](https://youtrack.jetbrains.com/issue/KTOR-745))
* Jetty: Content Length exception when body size is greater than 4096 bytes ([KTOR-4622](https://youtrack.jetbrains.com/issue/KTOR-4622))
* Darwin: configureRequest doesn't actually configure a NSMutableURLRequest when HTTP request is made ([KTOR-4719](https://youtrack.jetbrains.com/issue/KTOR-4719))
* OAuth2: Allow sending extra parameters for authorization and access token requests ([KTOR-2128](https://youtrack.jetbrains.com/issue/KTOR-2128))
* Java engine: Allow configuring HTTP version ([KTOR-4609](https://youtrack.jetbrains.com/issue/KTOR-4609))
* ContentEncoding: body&lt;ByteArray&gt;() receives truncated array ([KTOR-4653](https://youtrack.jetbrains.com/issue/KTOR-4653))
* Support configuring Netty codec limits via application config ([KTOR-4636](https://youtrack.jetbrains.com/issue/KTOR-4636))
* [OkHttp] StreamRequestBody should override isOneShot or allow multiple reads of request body ([KTOR-4637](https://youtrack.jetbrains.com/issue/KTOR-4637))
* OverridingClassLoader fails to delegate to parent for resources ([KTOR-4004](https://youtrack.jetbrains.com/issue/KTOR-4004))
* OkHttp and iOS: request with only-if-cache directive in Cache-Control header fails with 504 when match is stale ([KTOR-4127](https://youtrack.jetbrains.com/issue/KTOR-4127))
* Allow Pebble to use Accepted Language header for built-in i18n support ([KTOR-4593](https://youtrack.jetbrains.com/issue/KTOR-4593))
* Test engine can't handle concurrent requests ([KTOR-4572](https://youtrack.jetbrains.com/issue/KTOR-4572))
* Parameters of cloned UrlBuilder affect parameters of an original builder ([KTOR-4573](https://youtrack.jetbrains.com/issue/KTOR-4573))
* Reified type causes ApplicationCall.receive() throw UnsupportedOperationException ([KTOR-3715](https://youtrack.jetbrains.com/issue/KTOR-3715))
* ApplicationConfig lacks the ability to export a part of the config to a third-party library ([KTOR-4416](https://youtrack.jetbrains.com/issue/KTOR-4416))
* Path parameter doesn't get encoded in type safe requests ([KTOR-3953](https://youtrack.jetbrains.com/issue/KTOR-3953))
* Update Kotlin to 1.7.0 ([KTOR-4450](https://youtrack.jetbrains.com/issue/KTOR-4450))
* Bump jteVersion from 2.0.3 to 2.1.2 ([KTOR-4648](https://youtrack.jetbrains.com/issue/KTOR-4648))

# 2.0.3
> Published 28 June 2022

* Development mode class loader leads to ClassCastException within a CouroutineScope ([KTOR-4164](https://youtrack.jetbrains.com/issue/KTOR-4164))
* Validate that the body of an incoming request is received completely ([KTOR-4379](https://youtrack.jetbrains.com/issue/KTOR-4379))
* UrlBuilder escapes fragment parameters ([KTOR-4412](https://youtrack.jetbrains.com/issue/KTOR-4412))
* CallLogging: JVM crashes when jansi checks whether a file descriptor refers to a terminal ([KTOR-3476](https://youtrack.jetbrains.com/issue/KTOR-3476))
* WebSocket client closes connection due to an HTTP request timeout ([KTOR-4419](https://youtrack.jetbrains.com/issue/KTOR-4419))
* [JS client] Cannot change redirect policy by followRedirects=false ([KTOR-326](https://youtrack.jetbrains.com/issue/KTOR-326))
* CIO engine doesn't apply a request timeout from the `HttpTimeout` plugin ([KTOR-4473](https://youtrack.jetbrains.com/issue/KTOR-4473))
* CIO: Websockets request doesn't include query parameters ([KTOR-4390](https://youtrack.jetbrains.com/issue/KTOR-4390))
* Ignore SIGPIPE for server sockets ([KTOR-4474](https://youtrack.jetbrains.com/issue/KTOR-4474))
* Direct byte buffers are increased in size when server slowly processes request ([KTOR-4397](https://youtrack.jetbrains.com/issue/KTOR-4397))
* UDP responses are received with a huge delay on JVM Windows (due to reverse DNS lookup internally) ([KTOR-4423](https://youtrack.jetbrains.com/issue/KTOR-4423))
* "No instance for key AttributeKey: ApplicationPluginRegistry" when exception is thrown during the Call phase ([KTOR-4448](https://youtrack.jetbrains.com/issue/KTOR-4448))
* Non-decipherable exception "No result transformation found" ([KTOR-4287](https://youtrack.jetbrains.com/issue/KTOR-4287))
* Unable to set the Content-Type header in a request ([KTOR-620](https://youtrack.jetbrains.com/issue/KTOR-620))
* Update kotlinx.coroutines to 1.6.2 ([KTOR-4451](https://youtrack.jetbrains.com/issue/KTOR-4451))
* Support the HttpTimeout capability in the DelegatingTestClientEngine ([KTOR-4436](https://youtrack.jetbrains.com/issue/KTOR-4436))
* Limit the number of parallel running requests in Netty ([KTOR-4575](https://youtrack.jetbrains.com/issue/KTOR-4575))
* Resources plugin fails to process parameters of type UShort ([KTOR-4424](https://youtrack.jetbrains.com/issue/KTOR-4424))
* Resources plugin doesn't respect default values for Enum ([KTOR-4411](https://youtrack.jetbrains.com/issue/KTOR-4411))
* Invalid request line produced by CIO engine for URL with parameters and without path ([KTOR-4347](https://youtrack.jetbrains.com/issue/KTOR-4347))
* call.receiveText() tries to parse body as JSON when the ContentNegotiation plugin is installed ([KTOR-4426](https://youtrack.jetbrains.com/issue/KTOR-4426))
* Ignore ByteReadChannel as receive type in ContentNegotiation ([KTOR-4511](https://youtrack.jetbrains.com/issue/KTOR-4511))
* Setting body to TextContent leads to NPE when the ContentNegotiation plugin is installed ([KTOR-4383](https://youtrack.jetbrains.com/issue/KTOR-4383))
* submitFormWithBinaryData call leads to NPE when the ContentNegotiation plugin is installed ([KTOR-4269](https://youtrack.jetbrains.com/issue/KTOR-4269))
* ResponseConverter NPE when returning ByteArray with the ContentNegotiation plugin ([KTOR-4399](https://youtrack.jetbrains.com/issue/KTOR-4399))

# 2.0.2
> Published 27 May 2022

* [iOS] Prevent HttpClient from persisting cookies across requests ([KTOR-3748](https://youtrack.jetbrains.com/issue/KTOR-3748))
* Web feedback from "Creating HTTP APIs", https://ktor.io/docs/creating-http-apis.html ([KTOR-4380](https://youtrack.jetbrains.com/issue/KTOR-4380))
* When returning a String, content negotiation is ignored ([KTOR-662](https://youtrack.jetbrains.com/issue/KTOR-662))
* HttpResponse.bodyAsChannel should not be converted by ContentNegotiation ([KTOR-4341](https://youtrack.jetbrains.com/issue/KTOR-4341))
* Strings are not decoded when received as application/json ([KTOR-385](https://youtrack.jetbrains.com/issue/KTOR-385))
* Document how to enable/disable HTTP/2 for different client engines ([KTOR-4340](https://youtrack.jetbrains.com/issue/KTOR-4340))
* Revert Dokka to 1.6.10 due to Publication Freeze ([KTOR-4290](https://youtrack.jetbrains.com/issue/KTOR-4290))
* Document a new memory model in KMM tutorial ([KTOR-4354](https://youtrack.jetbrains.com/issue/KTOR-4354))
* Make client docs less JVM-centric ([KTOR-4351](https://youtrack.jetbrains.com/issue/KTOR-4351))
* Darwin engine: Client connection is closed after each request ([KTOR-4145](https://youtrack.jetbrains.com/issue/KTOR-4145))
* Ios: NullPointerException when query parameters contain cyrillic symbols in values ([KTOR-1858](https://youtrack.jetbrains.com/issue/KTOR-1858))
* A native application with the Darwin engine doesn't make a request ([KTOR-3900](https://youtrack.jetbrains.com/issue/KTOR-3900))
* Darwin and Kotlin/JS: "List has more than one element" error when header like Content-type is duplicated in a response ([KTOR-4105](https://youtrack.jetbrains.com/issue/KTOR-4105))
* Invalid response without error ([KTOR-369](https://youtrack.jetbrains.com/issue/KTOR-369))
* Invalid HTTP version should fail ([KTOR-380](https://youtrack.jetbrains.com/issue/KTOR-380))
* The colon after the host parameter requires a port ([KTOR-382](https://youtrack.jetbrains.com/issue/KTOR-382))
* Kotlin/Native: testApplication's client sometimes fails to receive ByteArray response from a route ([KTOR-4197](https://youtrack.jetbrains.com/issue/KTOR-4197))
* "Application started" is never printed ([KTOR-4319](https://youtrack.jetbrains.com/issue/KTOR-4319))
* Default request without explicit port sets port 80 for all requests ([KTOR-4281](https://youtrack.jetbrains.com/issue/KTOR-4281))
* Documentation about how to configure libcurl on Windows ([KTOR-3988](https://youtrack.jetbrains.com/issue/KTOR-3988))
* API Docs reference RFCs. Better to reference our own documentation ([KTOR-3764](https://youtrack.jetbrains.com/issue/KTOR-3764))
* UninitializedPropertyAccessException in the handleResponseExceptionWithRequest when request or response are accessed through  ([KTOR-4230](https://youtrack.jetbrains.com/issue/KTOR-4230))HttpClientCall
* The original exception is swallowed by "No request transformation found" exception when request body is serializable object ([KTOR-4160](https://youtrack.jetbrains.com/issue/KTOR-4160))
* IncorrectDereferenceException when trying to create HttpClient from background thread on iOS ([KTOR-4263](https://youtrack.jetbrains.com/issue/KTOR-4263))
* JacksonWebsocketContentConverter.deserialize just doesn't work ([KTOR-4248](https://youtrack.jetbrains.com/issue/KTOR-4248))
* Documentation for migration of Authentication server plugin ([KTOR-4253](https://youtrack.jetbrains.com/issue/KTOR-4253))
* Add sample for the AuthenticationChecked hook ([KTOR-4278](https://youtrack.jetbrains.com/issue/KTOR-4278))
* Web feedback from "Docker", https://ktor.io/docs/docker.html ([KTOR-4282](https://youtrack.jetbrains.com/issue/KTOR-4282))
* Route's path parameters are empty when ApplicationCall.authentication is first accessed in a different ApplicationCall context ([KTOR-4250](https://youtrack.jetbrains.com/issue/KTOR-4250))
* Routes with tailcard should not count for specific http error codes ([KTOR-4280](https://youtrack.jetbrains.com/issue/KTOR-4280))
* Documentation for appending query parameters for URL in the DefaultRequest ([KTOR-4252](https://youtrack.jetbrains.com/issue/KTOR-4252))
* Routing returns 405 even for not completely matched paths ([KTOR-4267](https://youtrack.jetbrains.com/issue/KTOR-4267))
* Resources: builder methods return routes with PathSegmentConstantRouteSelector instead of HttpMethodRouteSelector ([KTOR-4239](https://youtrack.jetbrains.com/issue/KTOR-4239))
* Update Netty to 4.1.77.Final ([KTOR-4339](https://youtrack.jetbrains.com/issue/KTOR-4339))
* External services should use config from environment ([KTOR-4373](https://youtrack.jetbrains.com/issue/KTOR-4373))
* Update Jackson to 2.13.3 ([KTOR-4394](https://youtrack.jetbrains.com/issue/KTOR-4394))

# 2.0.1
> Published 28 April 2022

* Fix URL representation ([KTOR-4241](https://youtrack.jetbrains.com/issue/KTOR-4241))
* embeddedServer for CIO and Netty inconsistency ([KTOR-755](https://youtrack.jetbrains.com/issue/KTOR-755))
* Update Coroutines to 1.6.1 ([KTOR-4240](https://youtrack.jetbrains.com/issue/KTOR-4240))
* Locations: Support trailing / ([KTOR-836](https://youtrack.jetbrains.com/issue/KTOR-836))
* Resources: Make `Route.handle` public ([KTOR-4200](https://youtrack.jetbrains.com/issue/KTOR-4200))
* Fix CURL flaky initialization ([KTOR-4223](https://youtrack.jetbrains.com/issue/KTOR-4223))
* Optimize Slow Native Tests ([KTOR-4224](https://youtrack.jetbrains.com/issue/KTOR-4224))
* Print Native Stacktrace on Timeout ([KTOR-4198](https://youtrack.jetbrains.com/issue/KTOR-4198))
* """IllegalStateException: Operation is already in progress"" when the readByte is called the second time after a timeout" ([KTOR-4218](https://youtrack.jetbrains.com/issue/KTOR-4218))
* Update Kotlin to 1.6.21 ([KTOR-4221](https://youtrack.jetbrains.com/issue/KTOR-4221))
* Update code for editing an article in the 'Interactive website' tutorial ([KTOR-4227](https://youtrack.jetbrains.com/issue/KTOR-4227))
* DefaultRequest: HTTPS protocol isn't set when using Ktor 2.0.0 ([KTOR-4142](https://youtrack.jetbrains.com/issue/KTOR-4142))
* DefaultRequest: host and port aren't used for a request ([KTOR-4154](https://youtrack.jetbrains.com/issue/KTOR-4154))
* A table with test methods should span the entire width of the dialog ([KTOR-4064](https://youtrack.jetbrains.com/issue/KTOR-4064))
* StatusPages plugin does not handle most specific exception in Ktor 2.0.0 ([KTOR-4187](https://youtrack.jetbrains.com/issue/KTOR-4187))
* Behaviour of ApplicationEngine start method not documented properly ([KTOR-2271](https://youtrack.jetbrains.com/issue/KTOR-2271))
* CORS plugin should be route scoped ([KTOR-4157](https://youtrack.jetbrains.com/issue/KTOR-4157))
* Raw Web Socket Connection Suspending Forever ([KTOR-4166](https://youtrack.jetbrains.com/issue/KTOR-4166))
* StatusPages: SerializationException isn't handled when CallID plugin is installed after StatusPages plugin ([KTOR-4155](https://youtrack.jetbrains.com/issue/KTOR-4155))
* HttpClient.wss defaults to port 80 instead of 443 ([KTOR-4175](https://youtrack.jetbrains.com/issue/KTOR-4175))
* Missing subject parameter in StatusPages `status` config method ([KTOR-4191](https://youtrack.jetbrains.com/issue/KTOR-4191))
* ConditionalHeaders cause the Last-Modified header appears twice in a response (2.0.0) ([KTOR-4163](https://youtrack.jetbrains.com/issue/KTOR-4163))
* DefaultHeaders: The Server header appears twice in a response (2.0.0) ([KTOR-4152](https://youtrack.jetbrains.com/issue/KTOR-4152))
* Testing: Resolving a substitution to a value in default config fails when custom HOCON config is used ([KTOR-4130](https://youtrack.jetbrains.com/issue/KTOR-4130))
* Combination of HttpCache and Logging plugins cause receiving incomplete response body for chunked replies ([KTOR-3916](https://youtrack.jetbrains.com/issue/KTOR-3916))

* # 2.0.0
> Published 11 April 2022

* HttpClient breaks permanently when certain exceptions occur while consuming ByteReadChannel ([KTOR-3140](https://youtrack.jetbrains.com/issue/KTOR-3140))
* Fix Dokka publication for 2.0.0 ([KTOR-4194](https://youtrack.jetbrains.com/issue/KTOR-4194))
* [Doc] invalid KDoc link for https://ktor.io/docs/http-client-engines.html#darwin ([KTOR-4165](https://youtrack.jetbrains.com/issue/KTOR-4165))
* Update the 'Manual Configuration' help link after the 2.0.0 release ([KTOR-3678](https://youtrack.jetbrains.com/issue/KTOR-3678))
* Where did 1.6.8 docs go? ([KTOR-4147](https://youtrack.jetbrains.com/issue/KTOR-4147))
* Fail to create response observer in different native thread. ([KTOR-3278](https://youtrack.jetbrains.com/issue/KTOR-3278))
* "Ktor app with Kotlin/Native fails with ""There is no event loop. Use runBlocking { ... } to start one.""" ([KTOR-4149](https://youtrack.jetbrains.com/issue/KTOR-4149))
* Update limitations for Kotlin/Native ([KTOR-4143](https://youtrack.jetbrains.com/issue/KTOR-4143))
* UDP sockets on native ([KTOR-1159](https://youtrack.jetbrains.com/issue/KTOR-1159))
* Ktor stopped working with latest Tomcat 9.0.39 ([KTOR-1172](https://youtrack.jetbrains.com/issue/KTOR-1172))
* Update to Kotlin 1.6.20 ([KTOR-4107](https://youtrack.jetbrains.com/issue/KTOR-4107))
* ContentNegotiation: the plugin removes Content-Type header even when a matching registration is not found ([KTOR-4091](https://youtrack.jetbrains.com/issue/KTOR-4091))
* JMXReporter not included in ktor-metrics:1.6.8 ([KTOR-4102](https://youtrack.jetbrains.com/issue/KTOR-4102))
* Performance Issue / Ktor & Netty ([KTOR-610](https://youtrack.jetbrains.com/issue/KTOR-610))
* httpMethod is not affected by X-Http-Method-Override (in opposite to docs) ([KTOR-404](https://youtrack.jetbrains.com/issue/KTOR-404))
* Android: Failed resolution of: Ljava/nio/file/Paths using API 25 and lower ([KTOR-3269](https://youtrack.jetbrains.com/issue/KTOR-3269))
* ContentNegotiation plugins don't accept null-responses from ContentConverts ([KTOR-3346](https://youtrack.jetbrains.com/issue/KTOR-3346))
* Using proguard and CallLogging feature causes JVM crashes ([KTOR-3345](https://youtrack.jetbrains.com/issue/KTOR-3345))
* Remove checking body transformation from ContentNegotation ([KTOR-3272](https://youtrack.jetbrains.com/issue/KTOR-3272))
* Feature: Use websockets with serialization ([KTOR-423](https://youtrack.jetbrains.com/issue/KTOR-423))
* Fix `testErrorHandling` with JS ([KTOR-3510](https://youtrack.jetbrains.com/issue/KTOR-3510))
* [netty] Headers are only flushed after first byte is written ([KTOR-3364](https://youtrack.jetbrains.com/issue/KTOR-3364))
* AttributeKey instance is identified by its identity instead of its name ([KTOR-3538](https://youtrack.jetbrains.com/issue/KTOR-3538))
* HttpCookies: parse / in the name of a cookie ([KTOR-3497](https://youtrack.jetbrains.com/issue/KTOR-3497))
* Returning Thymeleaf fragments from Routes ([KTOR-3624](https://youtrack.jetbrains.com/issue/KTOR-3624))
* Rewrite Thymeleaf to New Plugins API ([KTOR-3687](https://youtrack.jetbrains.com/issue/KTOR-3687))
* Rewrite HSTS to new plugins API ([KTOR-3752](https://youtrack.jetbrains.com/issue/KTOR-3752))
* Rewrite FreeMarker to new Plugins API ([KTOR-3751](https://youtrack.jetbrains.com/issue/KTOR-3751))
* Rewrite CachingHeaders to New Plugins API ([KTOR-3688](https://youtrack.jetbrains.com/issue/KTOR-3688))
* Implementation for Single Page Plugin ([KTOR-3635](https://youtrack.jetbrains.com/issue/KTOR-3635))
* Sockets no longer working on Android since 2.0.0-beta-1 ([KTOR-3659](https://youtrack.jetbrains.com/issue/KTOR-3659))
* Implementation for Single Page Plugin ([KTOR-3577](https://youtrack.jetbrains.com/issue/KTOR-3577))
* Content Negotiation: Gson: Should be able to return 400 for badly formatted request. ([KTOR-373](https://youtrack.jetbrains.com/issue/KTOR-373))
* Rewrite ConditionalHeaders to New  Plugins API ([KTOR-3759](https://youtrack.jetbrains.com/issue/KTOR-3759))
* Single Page Plugin ([KTOR-3531](https://youtrack.jetbrains.com/issue/KTOR-3531))
* HttpResponseValidator.handleResponseException should have access to request to provide valuable information in exceptions ([KTOR-3652](https://youtrack.jetbrains.com/issue/KTOR-3652))
* Build in feature for Single PAge Applications ([KTOR-515](https://youtrack.jetbrains.com/issue/KTOR-515))
* "ContentNegotiation: The ""charset=UTF-8"" part is added for the Content-Type header" ([KTOR-3799](https://youtrack.jetbrains.com/issue/KTOR-3799))
* Rewrite Netty Engine ([KTOR-3467](https://youtrack.jetbrains.com/issue/KTOR-3467))
* SinglePageApplication plugin returns 404 for non-existent paths ([KTOR-3944](https://youtrack.jetbrains.com/issue/KTOR-3944))
* Split packages in KTOR Client 2.00-BETA-1 ([KTOR-4106](https://youtrack.jetbrains.com/issue/KTOR-4106))
* Support WebSockets in Darwin engine ([KTOR-4093](https://youtrack.jetbrains.com/issue/KTOR-4093))
* Ktor http client with java engine uses incorrect timeout. ([KTOR-4058](https://youtrack.jetbrains.com/issue/KTOR-4058))
* ktor-client performance ([KTOR-506](https://youtrack.jetbrains.com/issue/KTOR-506))
* InvalidPathException in ApplicationEngineEnvironmentReloading ([KTOR-3831](https://youtrack.jetbrains.com/issue/KTOR-3831))
* "Screenshot with the new project wizard on the ""Create a new project﻿"" topic is outdated" ([KTOR-4020](https://youtrack.jetbrains.com/issue/KTOR-4020))
* Add possibility to fully configure metricName in ktor-server-metrics-micrometer ([KTOR-3302](https://youtrack.jetbrains.com/issue/KTOR-3302))
* Adding Native support to ktor-server-html-builder for 2.0.0 release ([KTOR-3972](https://youtrack.jetbrains.com/issue/KTOR-3972))
* Ktor: Allow overriding coroutine dispatcher in MockEngine ([KTOR-3230](https://youtrack.jetbrains.com/issue/KTOR-3230))
* HttpClient request hangs when Logging plugin is installed ([KTOR-3970](https://youtrack.jetbrains.com/issue/KTOR-3970))
* Ktor uses too much memory compared to other Http server libraries ([KTOR-3903](https://youtrack.jetbrains.com/issue/KTOR-3903))
* [client] MPP WebSockets client ([KTOR-751](https://youtrack.jetbrains.com/issue/KTOR-751))
* Native websocket client support ([KTOR-599](https://youtrack.jetbrains.com/issue/KTOR-599))
* Don't perform migrations for MPP projects ([KTOR-3812](https://youtrack.jetbrains.com/issue/KTOR-3812))
* Change visibility from internal to public to HttpResponse and HttpClientCall ([KTOR-3984](https://youtrack.jetbrains.com/issue/KTOR-3984))
* Rename the 'header' function to 'allowHeader' for consistency with similar functions ([KTOR-3980](https://youtrack.jetbrains.com/issue/KTOR-3980))
* The CallID plugin missing in a New Project wizard ([KTOR-2911](https://youtrack.jetbrains.com/issue/KTOR-2911))
* IDEA plugin missing the Session authentication ([KTOR-3359](https://youtrack.jetbrains.com/issue/KTOR-3359))
* Install plugin completion doesn't work with custom built version of Ktor from main ([KTOR-4031](https://youtrack.jetbrains.com/issue/KTOR-4031))
* Memory leak when Compression plugin is installed ([KTOR-4028](https://youtrack.jetbrains.com/issue/KTOR-4028))
* Make most useful hooks public ([KTOR-3797](https://youtrack.jetbrains.com/issue/KTOR-3797))
* The ShutDownUrl sample doesn't work in the latest EAP ([KTOR-4025](https://youtrack.jetbrains.com/issue/KTOR-4025))
* OAuth: scopes are separated by + that's encoded to %2B ([KTOR-3945](https://youtrack.jetbrains.com/issue/KTOR-3945))
* The Caching headers plugin stops working in the latest EAP ([KTOR-4022](https://youtrack.jetbrains.com/issue/KTOR-4022))
* The 'allowHeadersPrefixed' and 'allowHeaders' CORS functions works incorrectly ([KTOR-3979](https://youtrack.jetbrains.com/issue/KTOR-3979))
* Migrate Generator to New Testing API ([KTOR-3763](https://youtrack.jetbrains.com/issue/KTOR-3763))
* Support receiving OAuth code response as form post ([KTOR-3342](https://youtrack.jetbrains.com/issue/KTOR-3342))
* Ktor test websocket call hangs ([KTOR-4000](https://youtrack.jetbrains.com/issue/KTOR-4000))
* Implementation for Create `ktor-test` module with mocks of engine and clients for writing tests ([KTOR-3236](https://youtrack.jetbrains.com/issue/KTOR-3236))
* Remove mutex from call logging ([KTOR-3987](https://youtrack.jetbrains.com/issue/KTOR-3987))
* Add jte template support ([KTOR-3749](https://youtrack.jetbrains.com/issue/KTOR-3749))
* No contextual serializers when KotlinxSerializationConverter is used ([KTOR-3782](https://youtrack.jetbrains.com/issue/KTOR-3782))
* Add support for list size methods in PlaceholderList ([KTOR-3940](https://youtrack.jetbrains.com/issue/KTOR-3940))
* Missing headers in OutgoingContent ([KTOR-3758](https://youtrack.jetbrains.com/issue/KTOR-3758))
* ResponseObserver does not respect MDC context ([KTOR-2435](https://youtrack.jetbrains.com/issue/KTOR-2435))
* Binary compatibility issue with ktor-2.0.0-beta1 when using JDK 1.8 ([KTOR-3645](https://youtrack.jetbrains.com/issue/KTOR-3645))
* Enhance api for ConditionalHeaders usage ([KTOR-728](https://youtrack.jetbrains.com/issue/KTOR-728))
* localization issue with new project wizard - plugin page ([KTOR-3943](https://youtrack.jetbrains.com/issue/KTOR-3943))
* IDE action to migrate to 2.0.0 ([KTOR-3225](https://youtrack.jetbrains.com/issue/KTOR-3225))
* Add colors to CLI client ([KTOR-3929](https://youtrack.jetbrains.com/issue/KTOR-3929))
* Support macOs M1 in CLI generator ([KTOR-3922](https://youtrack.jetbrains.com/issue/KTOR-3922))
* Bearer Auth: refreshTokens callback blocks indefinitely when server returns 401 ([KTOR-3795](https://youtrack.jetbrains.com/issue/KTOR-3795))
* "ContentEncoding: ""Unexpected EOF: expected 10 more bytes"" when trying to decode HEAD response" ([KTOR-3781](https://youtrack.jetbrains.com/issue/KTOR-3781))
* Memory leak in ktor-client-curl ([KTOR-3767](https://youtrack.jetbrains.com/issue/KTOR-3767))
* Rename ApplicationPlugin<A, B, C> to BaseApplicationPlugin<A, B, C> ([KTOR-3873](https://youtrack.jetbrains.com/issue/KTOR-3873))
* Ktor Server and double receive break receiving of big files ([KTOR-3832](https://youtrack.jetbrains.com/issue/KTOR-3832))
* Setting Content-Length Header manually when using call.respondOutputStream ([KTOR-560](https://youtrack.jetbrains.com/issue/KTOR-560))
* Support for adding values to the MDC later on in the pipeline. ([KTOR-536](https://youtrack.jetbrains.com/issue/KTOR-536))
* Default request: Query parameters in default URL are overwritten ([KTOR-3793](https://youtrack.jetbrains.com/issue/KTOR-3793))
* Timeout in receiving streaming body breaks client ([KTOR-3704](https://youtrack.jetbrains.com/issue/KTOR-3704))
* Setting DefaultRequest.url.protocol on the client side breaks the ability to establish a ws connection ([KTOR-3890](https://youtrack.jetbrains.com/issue/KTOR-3890))
* Rename the 'io.ktor.resources.serialisation' package to '...serialization' for consistency ([KTOR-3842](https://youtrack.jetbrains.com/issue/KTOR-3842))
* Generator performance: cache Maven requests ([KTOR-3866](https://youtrack.jetbrains.com/issue/KTOR-3866))
* JS: Websocket errors are not being handled correctly ([KTOR-1726](https://youtrack.jetbrains.com/issue/KTOR-1726))
* Logback transient depencency from ktor-server-test-host ([KTOR-2038](https://youtrack.jetbrains.com/issue/KTOR-2038))
* Ktor plugin is asking to migrate to EAP versions ([KTOR-3609](https://youtrack.jetbrains.com/issue/KTOR-3609))
* "Module ""io.ktor:ktor-network (io.ktor:ktor-network-iosarm64)"" has a reference to symbol kotlinx.coroutines/SingleThreadDispatcher|null[0]" ([KTOR-3562](https://youtrack.jetbrains.com/issue/KTOR-3562))
* Retry on HttpCode or network error ([KTOR-572](https://youtrack.jetbrains.com/issue/KTOR-572))
* Server hangs indefinitely when responding to requests on android using version 2.0.0 ([KTOR-3653](https://youtrack.jetbrains.com/issue/KTOR-3653))
* IllegalStateException when writing in coroutine context backed by more than one thread ([KTOR-3801](https://youtrack.jetbrains.com/issue/KTOR-3801))
* References for kotlinx.serialization plugin sample code in a new Ktor project created with Maven build system are unresolved ([KTOR-3754](https://youtrack.jetbrains.com/issue/KTOR-3754))
* multipart/form-data requests: No way of streaming data asynchronously ([KTOR-3825](https://youtrack.jetbrains.com/issue/KTOR-3825))
* Migrate ForwardHeaderSupport to new API ([KTOR-3677](https://youtrack.jetbrains.com/issue/KTOR-3677))
* Sort endpoints in Endpoint view and when creating tests ([KTOR-3725](https://youtrack.jetbrains.com/issue/KTOR-3725))
* StackOverflowError when opening Enpoints view with local Routing function ([KTOR-3816](https://youtrack.jetbrains.com/issue/KTOR-3816))
* Provide an example how to use new MultiPartFormDataContent (#KTOR-325) ([KTOR-3549](https://youtrack.jetbrains.com/issue/KTOR-3549))
* Client docs for desktop are misleading ([KTOR-3813](https://youtrack.jetbrains.com/issue/KTOR-3813))
* When working with SessionStorage, write is called every time after read ([KTOR-3336](https://youtrack.jetbrains.com/issue/KTOR-3336))
* DefaultRequest API doc contains missing members ([KTOR-3800](https://youtrack.jetbrains.com/issue/KTOR-3800))
* testApplication: Add https EngineConnector ([KTOR-3810](https://youtrack.jetbrains.com/issue/KTOR-3810))
* Rewrite HttpsRedirect to New Plugins API ([KTOR-3668](https://youtrack.jetbrains.com/issue/KTOR-3668))
* Rewrite WebJars to New Plugins API ([KTOR-3667](https://youtrack.jetbrains.com/issue/KTOR-3667))
* Rewrite Metrics to New Plugins API ([KTOR-3666](https://youtrack.jetbrains.com/issue/KTOR-3666))
* Rewrite PartialContent to New Plugins API ([KTOR-3665](https://youtrack.jetbrains.com/issue/KTOR-3665))
* Rewrite CallId to New Plugins API ([KTOR-3352](https://youtrack.jetbrains.com/issue/KTOR-3352))
* Drop Before/After from new plugins API ([KTOR-3803](https://youtrack.jetbrains.com/issue/KTOR-3803))
* Performance: Don't store PSI elements in Ktor Url Mappings. Use Smart Reference or PSI Anchor, instead ([KTOR-3789](https://youtrack.jetbrains.com/issue/KTOR-3789))
* Infrastructure: Build with JDK 11 for all modules fails: Can't inline metric micrometer because it uses jvm target 8 ([KTOR-3712](https://youtrack.jetbrains.com/issue/KTOR-3712))
* The 'refreshTokens' callback isn't invoked when an API returns a 401 response without the 'WWW-Authenticate' header ([KTOR-3516](https://youtrack.jetbrains.com/issue/KTOR-3516))
* Add DslMarker to testApplication builder ([KTOR-3783](https://youtrack.jetbrains.com/issue/KTOR-3783))
* Prohibit Nesting of `install` Blocks for Client and Server Configuration ([KTOR-3333](https://youtrack.jetbrains.com/issue/KTOR-3333))
* In docs and generated Gradle, Prometheus is misspelled as Promteteus ([KTOR-3792](https://youtrack.jetbrains.com/issue/KTOR-3792))
* submitFormWithBinaryData: mutation attempt of frozen <object>@194c6a8 ([KTOR-2947](https://youtrack.jetbrains.com/issue/KTOR-2947))
* iOS: Failed to find HttpClientEngineContainer with new native memory model ([KTOR-3517](https://youtrack.jetbrains.com/issue/KTOR-3517))
* Rewrite CallLogging to new plugins API ([KTOR-3351](https://youtrack.jetbrains.com/issue/KTOR-3351))
* Drop @ExperimentalTime ([KTOR-3595](https://youtrack.jetbrains.com/issue/KTOR-3595))
* Using any Suspend or Coroutine function in Bearer Auth functions cause crash on iOS ([KTOR-3177](https://youtrack.jetbrains.com/issue/KTOR-3177))
* [iOS] InvalidMutabilityException: mutation attempt of frozen ([KTOR-1223](https://youtrack.jetbrains.com/issue/KTOR-1223))
* InvalidMutabilityException: Configuration issues for ios ([KTOR-1251](https://youtrack.jetbrains.com/issue/KTOR-1251))
* iOS testing MockEngine issue ([KTOR-1541](https://youtrack.jetbrains.com/issue/KTOR-1541))
* """InvalidMutabilityException: Frozen during lazy computation"" when using by lazy for HttpClient" ([KTOR-1087](https://youtrack.jetbrains.com/issue/KTOR-1087))
* kotlin.native.concurrent.InvalidMutabilityException: mutation attempt of frozen kotlin.collections on iOS when deserializing class that contains less properties than the json ([KTOR-2740](https://youtrack.jetbrains.com/issue/KTOR-2740))
* Native: Cannot mutate objects inside onDownload and onUpload lambdas ([KTOR-3068](https://youtrack.jetbrains.com/issue/KTOR-3068))
* "HttpClient / native: ""mutation attempt of frozen"" crash when configuring the client" ([KTOR-1628](https://youtrack.jetbrains.com/issue/KTOR-1628))
* Ktor Kotlin Multiplatform leak ([KTOR-3586](https://youtrack.jetbrains.com/issue/KTOR-3586))
* Put label to local history before performing migration in Ktor ([KTOR-3716](https://youtrack.jetbrains.com/issue/KTOR-3716))
* StatusPages plugin continues call after calling handler ([KTOR-3707](https://youtrack.jetbrains.com/issue/KTOR-3707))
* StatusPages not returning code 500 on catched exception ([KTOR-3721](https://youtrack.jetbrains.com/issue/KTOR-3721))
* Rewrite Compression to New Plugins API ([KTOR-3661](https://youtrack.jetbrains.com/issue/KTOR-3661))
* Rewrite Auto Head to New Plugins API ([KTOR-3670](https://youtrack.jetbrains.com/issue/KTOR-3670))
* Rewrite DoubleReceive to New Plugins API ([KTOR-3672](https://youtrack.jetbrains.com/issue/KTOR-3672))
* Make default charset UTF-8 when using `receiveText` for application/json request ([KTOR-789](https://youtrack.jetbrains.com/issue/KTOR-789))
* Rewrite CORS to New Plugins API ([KTOR-3663](https://youtrack.jetbrains.com/issue/KTOR-3663))
* Rewrite Auth to New Plugins API ([KTOR-3660](https://youtrack.jetbrains.com/issue/KTOR-3660))
* Rewrite Sessions to New Plugins API ([KTOR-3664](https://youtrack.jetbrains.com/issue/KTOR-3664))
* Rewrite ContentNegotiation to New Plugins API ([KTOR-3669](https://youtrack.jetbrains.com/issue/KTOR-3669))
* Rewrite MethodOverride to New Plugins API ([KTOR-3662](https://youtrack.jetbrains.com/issue/KTOR-3662))
* Client logging: no description of default loggers' behavior on different platforms ([KTOR-3421](https://youtrack.jetbrains.com/issue/KTOR-3421))
* Update logback and slf4j ([KTOR-3733](https://youtrack.jetbrains.com/issue/KTOR-3733))
* NoClassDefFoundError is thrown on Android because ktor-utils references a not supported Java API ([KTOR-3690](https://youtrack.jetbrains.com/issue/KTOR-3690))
* Ktor-Utils references a Java API not supported by Android ([KTOR-3426](https://youtrack.jetbrains.com/issue/KTOR-3426))
* Migrate plugins to multiplatform ([KTOR-3539](https://youtrack.jetbrains.com/issue/KTOR-3539))
* ByteBufferChannel leaves unflushed data after partial readAvailable causing Apache client request to stall ([KTOR-3730](https://youtrack.jetbrains.com/issue/KTOR-3730))
* Migrate DefaultHeaders to new API ([KTOR-3676](https://youtrack.jetbrains.com/issue/KTOR-3676))
* ByteReadPacket.headerSizeHint is unused ([KTOR-3632](https://youtrack.jetbrains.com/issue/KTOR-3632))
* Hooks don't work with routing scoped plugins ([KTOR-3740](https://youtrack.jetbrains.com/issue/KTOR-3740))
* Client request builder: add shortcuts for authentication headers ([KTOR-2876](https://youtrack.jetbrains.com/issue/KTOR-2876))
* Pull Request - KTOR-404 Introduce support for X-Http-Method-Override ([KTOR-1825](https://youtrack.jetbrains.com/issue/KTOR-1825))
* Can't set a base url that includes path data ([KTOR-730](https://youtrack.jetbrains.com/issue/KTOR-730))
* Mention about closing ActorSelector manager ([KTOR-269](https://youtrack.jetbrains.com/issue/KTOR-269))
* Migrate to new kotlinx.coroutines and `limited` dispatcher(revert corePoolSize option) ([KTOR-3463](https://youtrack.jetbrains.com/issue/KTOR-3463))
* Routing is called for handled requests ([KTOR-3732](https://youtrack.jetbrains.com/issue/KTOR-3732))
* TomCat Documentation ([KTOR-2395](https://youtrack.jetbrains.com/issue/KTOR-2395))
* default resource package don't work ([KTOR-3722](https://youtrack.jetbrains.com/issue/KTOR-3722))
* webSocketSession method suspends indefinitely when there in connection error (Ktor beta) ([KTOR-3654](https://youtrack.jetbrains.com/issue/KTOR-3654))
* FUS metrics in IDE ([KTOR-2775](https://youtrack.jetbrains.com/issue/KTOR-2775))
* Update Documentation and Code for DoubleReceive Feature ([KTOR-1876](https://youtrack.jetbrains.com/issue/KTOR-1876))
* Add Defaults for the server.stop Method ([KTOR-3505](https://youtrack.jetbrains.com/issue/KTOR-3505))
* HTTP/2 not working with Netty ([KTOR-3705](https://youtrack.jetbrains.com/issue/KTOR-3705))
* Include changes from hands-on PR: Update 03_customer-routes.md #120 ([KTOR-3713](https://youtrack.jetbrains.com/issue/KTOR-3713))
* Nested routing fails to match route ([KTOR-1626](https://youtrack.jetbrains.com/issue/KTOR-1626))
* URLBuilder from string with trailing slash or from `Url` with no trailing slash, produces double slash when appending segments ([KTOR-3618](https://youtrack.jetbrains.com/issue/KTOR-3618))
* Provide the capability to generate WebSocket tests ([KTOR-3061](https://youtrack.jetbrains.com/issue/KTOR-3061))
* Add modulepath support for Java >= 9 ([KTOR-619](https://youtrack.jetbrains.com/issue/KTOR-619))
* HttpRequestRetry plugin expects Retry-After header value to be in milliseconds ([KTOR-3634](https://youtrack.jetbrains.com/issue/KTOR-3634))
* StringValuesBuilder.appendIfNameAbsent appends only if name is already present ([KTOR-3650](https://youtrack.jetbrains.com/issue/KTOR-3650))
* Migrations of the client code are not working for queries with non-trivial expression body ([KTOR-3703](https://youtrack.jetbrains.com/issue/KTOR-3703))
* Curl Cinterop compilation is failed on MacOS ([KTOR-3681](https://youtrack.jetbrains.com/issue/KTOR-3681))
* Compression slow due to using BEST_COMPRESSION for deflate/gzip ([KTOR-3680](https://youtrack.jetbrains.com/issue/KTOR-3680))
* Could not resolve: io.ktor:ktor-locations:2.0.0-beta-1 on a new project created with IDEA 2021.3.1 ([KTOR-3639](https://youtrack.jetbrains.com/issue/KTOR-3639))
* Update URL for the 'Adding Ktor dependencies' topic and add redirects ([KTOR-3673](https://youtrack.jetbrains.com/issue/KTOR-3673))
* Support package split in Ktor migrations in plugin (java modules support) ([KTOR-3679](https://youtrack.jetbrains.com/issue/KTOR-3679))
* Rewrite StatusPages with the new plugins API ([KTOR-3312](https://youtrack.jetbrains.com/issue/KTOR-3312))
* "Save ""Create Run Configuration automatically"" within .idea directory" ([KTOR-3282](https://youtrack.jetbrains.com/issue/KTOR-3282))
* java.lang.NoSuchMethodError: java.nio.ByteBuffer.limit(I)Ljava/nio/ByteBuffer when Ktor is built with JDK 9+ ([KTOR-1398](https://youtrack.jetbrains.com/issue/KTOR-1398))
* TestHttpClientEngine doesn't support HTTPS requests ([KTOR-3614](https://youtrack.jetbrains.com/issue/KTOR-3614))
* Endpoints view: Endpoints not populated if routes require authentication ([KTOR-3182](https://youtrack.jetbrains.com/issue/KTOR-3182))
* Insecure user session samples in documentation ([KTOR-3582](https://youtrack.jetbrains.com/issue/KTOR-3582))
* HttpRequestTimeoutException should not inherit CancellationException in ktor http client ([KTOR-3192](https://youtrack.jetbrains.com/issue/KTOR-3192))
* Update Documentation and Code for HSTS Feature ([KTOR-1878](https://youtrack.jetbrains.com/issue/KTOR-1878))
* Reduce the number of versions displayed in a plugin ([KTOR-3250](https://youtrack.jetbrains.com/issue/KTOR-3250))
* Update Documentation and Code for Webjars Feature ([KTOR-1885](https://youtrack.jetbrains.com/issue/KTOR-1885))
* XForwardedHeaderSupport should let you specify which index (from end) to choose ([KTOR-565](https://youtrack.jetbrains.com/issue/KTOR-565))
* Make migrations more configurable ([KTOR-3617](https://youtrack.jetbrains.com/issue/KTOR-3617))
* EAP Naming: main-number conflicts with dependencies ([KTOR-2724](https://youtrack.jetbrains.com/issue/KTOR-2724))
* ktor.io/learn typo ([KTOR-3563](https://youtrack.jetbrains.com/issue/KTOR-3563))
* Update Documentation and Code for HttpsRedirect Feature ([KTOR-1879](https://youtrack.jetbrains.com/issue/KTOR-1879))
* Provide better support for Ktor clients ([KTOR-883](https://youtrack.jetbrains.com/issue/KTOR-883))
* Multiple messages around upgrading to new version ([KTOR-3494](https://youtrack.jetbrains.com/issue/KTOR-3494))
* HttpRequestRetry in KTOR 2.0 should allow for request altering between retries ([KTOR-3544](https://youtrack.jetbrains.com/issue/KTOR-3544))
* Deploy Ktor application to docker topic contains hard coded project name ([KTOR-2852](https://youtrack.jetbrains.com/issue/KTOR-2852))
* Improvements for Docker sample in documentation ([KTOR-3294](https://youtrack.jetbrains.com/issue/KTOR-3294))
* """io.ktor.serializaion.gson"" - package naming in 2.0" ([KTOR-3527](https://youtrack.jetbrains.com/issue/KTOR-3527))
* Drop `client.get` Operator Because of Ambiguity with `get(URL)` ([KTOR-3487](https://youtrack.jetbrains.com/issue/KTOR-3487))
* "Option ""Add imports for Ktor modules automatically"" doesn't work" ([KTOR-3226](https://youtrack.jetbrains.com/issue/KTOR-3226))
* Migrations are unavailable ([KTOR-3570](https://youtrack.jetbrains.com/issue/KTOR-3570))
* Pull Request - fix #1970 - update MultiPartFormDataContent to allow contentType override using optional builder ([KTOR-1833](https://youtrack.jetbrains.com/issue/KTOR-1833))
* Pull Request - KTOR-1264 - Add UUID to DefaultConversionService ([KTOR-1815](https://youtrack.jetbrains.com/issue/KTOR-1815))
* Pull Request - Intercept pipeline at Setup phase for XForwardedHeaderSupport feature… ([KTOR-1844](https://youtrack.jetbrains.com/issue/KTOR-1844))
* Pull Request - Add locale to ThymeleafContent ([KTOR-1838](https://youtrack.jetbrains.com/issue/KTOR-1838))
* Objections to changing boundary to internal on MultiPartFormDataContent? ([KTOR-325](https://youtrack.jetbrains.com/issue/KTOR-325))
* Fix Log Size for Java 11 Windows Build ([KTOR-3535](https://youtrack.jetbrains.com/issue/KTOR-3535))
* ByteChannelSequential freezes after closing due to race condition ([KTOR-2776](https://youtrack.jetbrains.com/issue/KTOR-2776))
* Apple Arm: 'Resolving NPM dependencies using yarn' returns 139 ([KTOR-3561](https://youtrack.jetbrains.com/issue/KTOR-3561))
* Change log level from `INFO` to `ERROR` for tests only ([KTOR-3466](https://youtrack.jetbrains.com/issue/KTOR-3466))
* Responding without contentLength freezes on CIO native ([KTOR-3492](https://youtrack.jetbrains.com/issue/KTOR-3492))
* webSocketSession freeze every time ([KTOR-3460](https://youtrack.jetbrains.com/issue/KTOR-3460))
* Exceptions are Swallowed in `HttpClient.wss` block ([KTOR-3461](https://youtrack.jetbrains.com/issue/KTOR-3461))
* Support receiving headers before sending body in CIO client engine ([KTOR-3491](https://youtrack.jetbrains.com/issue/KTOR-3491))
* Build and test on Apple Silicon Arm ([KTOR-3248](https://youtrack.jetbrains.com/issue/KTOR-3248))
* Prototype anchors in new plugins API ([KTOR-3392](https://youtrack.jetbrains.com/issue/KTOR-3392))
* Rename the 'Ios' client engine to more generic term to cover all Apple operating systems ([KTOR-3394](https://youtrack.jetbrains.com/issue/KTOR-3394))
* Update Samples to Ktor 2.0 ([KTOR-3218](https://youtrack.jetbrains.com/issue/KTOR-3218))
* Implement new `Locations` feature ([KTOR-1706](https://youtrack.jetbrains.com/issue/KTOR-1706))
* Fix old metadata publication ([KTOR-3469](https://youtrack.jetbrains.com/issue/KTOR-3469))
* JS Client doesn't support ServiceWorker ([KTOR-3448](https://youtrack.jetbrains.com/issue/KTOR-3448))
* Move Server Related Code from `ktor-http-cio` to `ktor-server-cio` ([KTOR-3462](https://youtrack.jetbrains.com/issue/KTOR-3462))
* Add Check if Feature is installed for `WebSocket` builders ([KTOR-3459](https://youtrack.jetbrains.com/issue/KTOR-3459))
* With test application should load environment from the `application.conf` ([KTOR-2794](https://youtrack.jetbrains.com/issue/KTOR-2794))
* TestEngineApplication - implement HttpClient API ([KTOR-2416](https://youtrack.jetbrains.com/issue/KTOR-2416))
* Inconsistent TestApplicationRequest and Client HttpRequestBuilder API's ([KTOR-1246](https://youtrack.jetbrains.com/issue/KTOR-1246))
* Server features instead of client in the client `install` block ([KTOR-3412](https://youtrack.jetbrains.com/issue/KTOR-3412))
* `ContentNegotiation` is missing in the plugins completion window ([KTOR-3411](https://youtrack.jetbrains.com/issue/KTOR-3411))
* Code Snippets use Groovy in build files as opposed to default Kotlin option for Wizard ([KTOR-2190](https://youtrack.jetbrains.com/issue/KTOR-2190))
* Improve documentation for native/Apple client engines ([KTOR-3375](https://youtrack.jetbrains.com/issue/KTOR-3375))
* IJ locked after attempt to create new run config in a dialog ([KTOR-3385](https://youtrack.jetbrains.com/issue/KTOR-3385))
* "High CPU consumption/Lock after project opening in org.jetbrains.kotlin.storage.getValue ; org.jetbrains.kotlin.idea.caches.resolve.IdeaResolverForProject" ([KTOR-3337](https://youtrack.jetbrains.com/issue/KTOR-3337))
* Update Ktor Plugin Description ([KTOR-3388](https://youtrack.jetbrains.com/issue/KTOR-3388))
* Add explicit menu action for migration ([KTOR-3400](https://youtrack.jetbrains.com/issue/KTOR-3400))
* Project Generated with eap-256 has Errors in Imports ([KTOR-3397](https://youtrack.jetbrains.com/issue/KTOR-3397))
* Update non-generic samples to 2.0 ([KTOR-3285](https://youtrack.jetbrains.com/issue/KTOR-3285))
* Support New Native Memory Model ([KTOR-3217](https://youtrack.jetbrains.com/issue/KTOR-3217))
* Simplify plugin descriptions in wizard, remove empty options ([KTOR-3386](https://youtrack.jetbrains.com/issue/KTOR-3386))
* Server for Kotlin Native ([KTOR-746](https://youtrack.jetbrains.com/issue/KTOR-746))
* call.request.queryParameters decode plus as space ([KTOR-3297](https://youtrack.jetbrains.com/issue/KTOR-3297))
* Migrate existing plugins to RoutingScoped ([KTOR-3201](https://youtrack.jetbrains.com/issue/KTOR-3201))
* Bearer Authentication: Queue requests until refresh of tokens is completed ([KTOR-3325](https://youtrack.jetbrains.com/issue/KTOR-3325))
* Article about storing sensitive data and accessing it in application.conf ([KTOR-3340](https://youtrack.jetbrains.com/issue/KTOR-3340))
* Add parameter for specifying content-length in ApplicationCall#respondBytes ([KTOR-3087](https://youtrack.jetbrains.com/issue/KTOR-3087))
* Update Documentation and Code for CallId Feature ([KTOR-1874](https://youtrack.jetbrains.com/issue/KTOR-1874))
* Passing port 0 to start server on random port doesn't publish correct port to log ([KTOR-3288](https://youtrack.jetbrains.com/issue/KTOR-3288))
* Allow application environment configuration when running via commandLineEnvironment ([KTOR-3027](https://youtrack.jetbrains.com/issue/KTOR-3027))
* XForwardedHeaderSupport is installed late in the pipeline ([KTOR-731](https://youtrack.jetbrains.com/issue/KTOR-731))
* Add locale to ThymeleafContent ([KTOR-3313](https://youtrack.jetbrains.com/issue/KTOR-3313))
* Add support for ports in withTestApplication ([KTOR-725](https://youtrack.jetbrains.com/issue/KTOR-725))
* Error in 2.0 doc/sample for HttpClient retry ([KTOR-3303](https://youtrack.jetbrains.com/issue/KTOR-3303))
* Cyclic dependency issue in latest 2.0 (main branch) ([KTOR-3240](https://youtrack.jetbrains.com/issue/KTOR-3240))
* An error occurred when running a sample with the configured XML serializer ([KTOR-3286](https://youtrack.jetbrains.com/issue/KTOR-3286))
* respondOutputStream behind nginx fails ([KTOR-346](https://youtrack.jetbrains.com/issue/KTOR-346))
* XML Support in Ktor ([KTOR-489](https://youtrack.jetbrains.com/issue/KTOR-489))
* Start ktor server on random port ([KTOR-686](https://youtrack.jetbrains.com/issue/KTOR-686))
* ProxyConfig.type checking for DIRECT instead of SOCKS ([KTOR-1733](https://youtrack.jetbrains.com/issue/KTOR-1733))
* Freeze the screen when I create routes ([KTOR-3004](https://youtrack.jetbrains.com/issue/KTOR-3004))
* Client: DefaultRequest apply defaults before request builder ([KTOR-2877](https://youtrack.jetbrains.com/issue/KTOR-2877))
* KDoc: HttpRequestBuilder.header actually appends header value, does not set it ([KTOR-2492](https://youtrack.jetbrains.com/issue/KTOR-2492))
* parameterOf() should have a variant that takes in a Map<String, List<String>> ([KTOR-399](https://youtrack.jetbrains.com/issue/KTOR-399))
* TLS relared tests are failing on CI ([KTOR-3224](https://youtrack.jetbrains.com/issue/KTOR-3224))
* [Ktor Client] CborFeature ([KTOR-3174](https://youtrack.jetbrains.com/issue/KTOR-3174))
* Jackson: receiveOrNull crashes with an exception when sending empty content ([KTOR-727](https://youtrack.jetbrains.com/issue/KTOR-727))
* Jackson-backed `ApplicationCall.receive` does not throw `ContentTransformationException` ([KTOR-614](https://youtrack.jetbrains.com/issue/KTOR-614))
* Remove Obsolete Check Cast from SuspendFunctionGun ([KTOR-3178](https://youtrack.jetbrains.com/issue/KTOR-3178))
* Ktor: Fold internal stack frames for HTTP server ([KTOR-2274](https://youtrack.jetbrains.com/issue/KTOR-2274))
* Support 2.0.0 in IDE ([KTOR-3196](https://youtrack.jetbrains.com/issue/KTOR-3196))
* Client HttpCache feature is not documented ([KTOR-1279](https://youtrack.jetbrains.com/issue/KTOR-1279))
* Feature to Plugin changes in Documentation ([KTOR-2372](https://youtrack.jetbrains.com/issue/KTOR-2372))
* Update server dependencies and imports in docs for 2.0.0 ([KTOR-3150](https://youtrack.jetbrains.com/issue/KTOR-3150))
* Add method to Client and ServerResponseException ([KTOR-3128](https://youtrack.jetbrains.com/issue/KTOR-3128))
* Add UUID to DefaultConversionService ([KTOR-1264](https://youtrack.jetbrains.com/issue/KTOR-1264))
* Prioritize text found in feature titles over descriptions ([KTOR-2488](https://youtrack.jetbrains.com/issue/KTOR-2488))
* SerializationException when serializing request body object of generic class type ([KTOR-1019](https://youtrack.jetbrains.com/issue/KTOR-1019))
* The 'Create test for Ktor module' intention actions changes files from other modules for a multimodule Gradle project ([KTOR-3062](https://youtrack.jetbrains.com/issue/KTOR-3062))
* Implementation for Simple API for writing features ([KTOR-2480](https://youtrack.jetbrains.com/issue/KTOR-2480))
* Wizard Plugin listing strange link ([KTOR-2882](https://youtrack.jetbrains.com/issue/KTOR-2882))
* Add filtering support in Ktor client response interceptor ([KTOR-2992](https://youtrack.jetbrains.com/issue/KTOR-2992))
* Nothing happens when no test routes is selected when generating Ktor test for module ([KTOR-3095](https://youtrack.jetbrains.com/issue/KTOR-3095))
* The 'Create test for Ktor module' intention action doesn't create any tests if routes are defined inside the extension function ([KTOR-3079](https://youtrack.jetbrains.com/issue/KTOR-3079))
* Allow using the client itself inside Auth plugin in the refreshTokens lambda. ([KTOR-2977](https://youtrack.jetbrains.com/issue/KTOR-2977))
* ADE at io.ktor.ide.plugins.add.KtorMarketplacePluginsUpdater.checkForUpdates ([KTOR-3076](https://youtrack.jetbrains.com/issue/KTOR-3076))
* Define completion priorities for Ktor keywords ([KTOR-2773](https://youtrack.jetbrains.com/issue/KTOR-2773))
* Adding features action in IDE ([KTOR-2893](https://youtrack.jetbrains.com/issue/KTOR-2893))
* Ktor Client JS: request to /example requests http://localhost/example ([KTOR-453](https://youtrack.jetbrains.com/issue/KTOR-453))
* URLBuilder: Move Default Values to build() function ([KTOR-1345](https://youtrack.jetbrains.com/issue/KTOR-1345))
* Implement design about moving features from ktor-server-core ([KTOR-1239](https://youtrack.jetbrains.com/issue/KTOR-1239))
* Move server code to io.ktor.server.* package ([KTOR-2865](https://youtrack.jetbrains.com/issue/KTOR-2865))
* Impossible to modify response headers ([KTOR-2822](https://youtrack.jetbrains.com/issue/KTOR-2822))
* ApplicationConfig: how to iterate over keys and values of config ([KTOR-2318](https://youtrack.jetbrains.com/issue/KTOR-2318))
* Missing Locations params result in 404 instead of 400 ([KTOR-447](https://youtrack.jetbrains.com/issue/KTOR-447))
* Implementation for  Events Feature For Client Metrics ([KTOR-2472](https://youtrack.jetbrains.com/issue/KTOR-2472))
* Should return 405 when route exists but not for given method instead of 404 ([KTOR-737](https://youtrack.jetbrains.com/issue/KTOR-737))
* Fix 2.0.0 branch compilation ([KTOR-2603](https://youtrack.jetbrains.com/issue/KTOR-2603))
* Query of pre-signed URL has been altered after decode and re-encode process ([KTOR-778](https://youtrack.jetbrains.com/issue/KTOR-778))
* ApplicationCall.locationOrNull raises error ([KTOR-1684](https://youtrack.jetbrains.com/issue/KTOR-1684))
* ContentConverter.convertForSend should receive a KType ([KTOR-444](https://youtrack.jetbrains.com/issue/KTOR-444))
* Make `body` nullable for request builder ([KTOR-1400](https://youtrack.jetbrains.com/issue/KTOR-1400))
* Send 100 Continue response only when getting a request to receive `IncomingContent` ([KTOR-855](https://youtrack.jetbrains.com/issue/KTOR-855))

# 1.6.8
> Published 14 March 2022

* Update Gradle to 7.4
* Update Kotlin to 1.6.10
* Migrate gradle to version catalog
* Update logback version to 1.2.11 ([KTOR-3935](https://youtrack.jetbrains.com/issue/KTOR-3935))
* Update atomicfu to 0.17.1
* Update netty to 4.1.74.Final
* Update netty-tcnative to 2.0.45.Final
* Update jetty to 9.4.45.v20220203
* Update tomcat to 9.0.59
* Update apache to 4.1.5
* Update okhttp to 4.9.3
* Update gson to 2.9.0
* Update jackson 2.13.1
* Update slf4j to 1.7.36
* Update node-fetch to 2.6.7
* Update js ws package to 8.5.0
* Revert wrong check to prevent anyHost with allowCredentials ([KTOR-2872](https://youtrack.jetbrains.com/issue/KTOR-2872)

# 2.0.0-beta-1
> Published 23 December 2021

* EAP Naming: main-number conflicts with dependencies ([KTOR-2724](https://youtrack.jetbrains.com/issue/KTOR-2724))
* ktor.io/learn typo ([KTOR-3563](https://youtrack.jetbrains.com/issue/KTOR-3563))
* Multiple messages around upgrading to new version ([KTOR-3494](https://youtrack.jetbrains.com/issue/KTOR-3494))
* Deploy Ktor application to docker topic contains hard coded project name ([KTOR-2852](https://youtrack.jetbrains.com/issue/KTOR-2852))
* Improvements for Docker sample in documentation ([KTOR-3294](https://youtrack.jetbrains.com/issue/KTOR-3294))
* "io.ktor.serializaion.gson" - package naming in 2.0 ([KTOR-3527](https://youtrack.jetbrains.com/issue/KTOR-3527))
* Drop `client.get` Operator Because of Ambiguity with `get(URL)` ([KTOR-3487](https://youtrack.jetbrains.com/issue/KTOR-3487))
* Option "Add imports for Ktor modules automatically" doesn't work ([KTOR-3226](https://youtrack.jetbrains.com/issue/KTOR-3226))
* Migrations are unavailable ([KTOR-3570](https://youtrack.jetbrains.com/issue/KTOR-3570))
* AttributeKey instance is identified by its identity instead of its name ([KTOR-3538](https://youtrack.jetbrains.com/issue/KTOR-3538))
* Fix Log Size for Java 11 Windows Build ([KTOR-3535](https://youtrack.jetbrains.com/issue/KTOR-3535))
* ByteChannelSequential freezes after closing due to race condition ([KTOR-2776](https://youtrack.jetbrains.com/issue/KTOR-2776))
* Apple Arm: 'Resolving NPM dependencies using yarn' returns 139 ([KTOR-3561](https://youtrack.jetbrains.com/issue/KTOR-3561))
* Change log level from `INFO` to `ERROR` for tests only ([KTOR-3466](https://youtrack.jetbrains.com/issue/KTOR-3466))
* Responding without contentLength freezes on CIO native ([KTOR-3492](https://youtrack.jetbrains.com/issue/KTOR-3492))
* webSocketSession freeze every time ([KTOR-3460](https://youtrack.jetbrains.com/issue/KTOR-3460))
* Exceptions are Swallowed in `HttpClient.wss` block ([KTOR-3461](https://youtrack.jetbrains.com/issue/KTOR-3461))
* Support receiving headers before sending body in CIO client engine ([KTOR-3491](https://youtrack.jetbrains.com/issue/KTOR-3491))
* [netty] Headers are only flushed after first byte is written ([KTOR-3364](https://youtrack.jetbrains.com/issue/KTOR-3364))
* Fix `testErrorHandling` with JS ([KTOR-3510](https://youtrack.jetbrains.com/issue/KTOR-3510))
* Build and test on Apple Silicon Arm ([KTOR-3248](https://youtrack.jetbrains.com/issue/KTOR-3248))
* Fix old metadata publication ([KTOR-3469](https://youtrack.jetbrains.com/issue/KTOR-3469))
* Remove checking body transformation from ContentNegotation ([KTOR-3272](https://youtrack.jetbrains.com/issue/KTOR-3272))
* Ktor-Utils references a Java API not supported by Android ([KTOR-3426](https://youtrack.jetbrains.com/issue/KTOR-3426))
* With test application should load environment from the `application.conf` ([KTOR-2794](https://youtrack.jetbrains.com/issue/KTOR-2794))
* Inconsistent TestApplicationRequest and Client HttpRequestBuilder API's ([KTOR-1246](https://youtrack.jetbrains.com/issue/KTOR-1246))
* Server features instead of client in the client `install` block ([KTOR-3412](https://youtrack.jetbrains.com/issue/KTOR-3412))
* Using proguard and CallLogging feature causes JVM crashes ([KTOR-3345](https://youtrack.jetbrains.com/issue/KTOR-3345))
* `ContentNegotiation` is missing in the plugins completion window ([KTOR-3411](https://youtrack.jetbrains.com/issue/KTOR-3411))
* Code Snippets use Groovy in build files as opposed to default Kotlin option for Wizard ([KTOR-2190](https://youtrack.jetbrains.com/issue/KTOR-2190))
* IJ locked after attempt to create new run config in a dialog ([KTOR-3385](https://youtrack.jetbrains.com/issue/KTOR-3385))
* ContentNegotiation plugins don't accept null-responses from ContentConverts ([KTOR-3346](https://youtrack.jetbrains.com/issue/KTOR-3346))
* High CPU consumption/Lock after project opening in org.jetbrains.kotlin.storage.getValue ; org.jetbrains.kotlin.idea.caches.resolve.IdeaResolverForProject ([KTOR-3337](https://youtrack.jetbrains.com/issue/KTOR-3337))
* Update Ktor Plugin Description ([KTOR-3388](https://youtrack.jetbrains.com/issue/KTOR-3388))
* Project Generated with eap-256 has Errors in Imports ([KTOR-3397](https://youtrack.jetbrains.com/issue/KTOR-3397))
* Update non-generic samples to 2.0 ([KTOR-3285](https://youtrack.jetbrains.com/issue/KTOR-3285))
* Simplify plugin descriptions in wizard, remove empty options ([KTOR-3386](https://youtrack.jetbrains.com/issue/KTOR-3386))
* Bearer Authentication: Queue requests until refresh of tokens is completed ([KTOR-3325](https://youtrack.jetbrains.com/issue/KTOR-3325))
* Article about storing sensitive data and accessing it in application.conf ([KTOR-3340](https://youtrack.jetbrains.com/issue/KTOR-3340))
* Android: Failed resolution of: Ljava/nio/file/Paths using API 25 and lower ([KTOR-3269](https://youtrack.jetbrains.com/issue/KTOR-3269))
* IDE action to migrate to 2.0.0 ([KTOR-3225](https://youtrack.jetbrains.com/issue/KTOR-3225))
* Passing port 0 to start server on random port doesn't publish correct port to log ([KTOR-3288](https://youtrack.jetbrains.com/issue/KTOR-3288))
* XForwardedHeaderSupport is installed late in the pipeline ([KTOR-731](https://youtrack.jetbrains.com/issue/KTOR-731))
* Error in 2.0 doc/sample for HttpClient retry ([KTOR-3303](https://youtrack.jetbrains.com/issue/KTOR-3303))
* Cyclic dependency issue in latest 2.0 (main branch) ([KTOR-3240](https://youtrack.jetbrains.com/issue/KTOR-3240))
* An error occurred when running a sample with the configured XML serializer ([KTOR-3286](https://youtrack.jetbrains.com/issue/KTOR-3286))
* respondOutputStream behind nginx fails ([KTOR-346](https://youtrack.jetbrains.com/issue/KTOR-346))
* ProxyConfig.type checking for DIRECT instead of SOCKS ([KTOR-1733](https://youtrack.jetbrains.com/issue/KTOR-1733))
* Freeze the screen when I create routes ([KTOR-3004](https://youtrack.jetbrains.com/issue/KTOR-3004))
* httpMethod is not affected by X-Http-Method-Override (in opposite to docs) ([KTOR-404](https://youtrack.jetbrains.com/issue/KTOR-404))
* Client: DefaultRequest apply defaults before request builder ([KTOR-2877](https://youtrack.jetbrains.com/issue/KTOR-2877))
* KDoc: HttpRequestBuilder.header actually appends header value, does not set it ([KTOR-2492](https://youtrack.jetbrains.com/issue/KTOR-2492))
* TLS relared tests are failing on CI ([KTOR-3224](https://youtrack.jetbrains.com/issue/KTOR-3224))
* Jackson: receiveOrNull crashes with an exception when sending empty content ([KTOR-727](https://youtrack.jetbrains.com/issue/KTOR-727))
* Content Negotiation: Gson: Should be able to return 400 for badly formatted request. ([KTOR-373](https://youtrack.jetbrains.com/issue/KTOR-373))
* Jackson-backed `ApplicationCall.receive` does not throw `ContentTransformationException` ([KTOR-614](https://youtrack.jetbrains.com/issue/KTOR-614))
* Remove Obsolete Check Cast from SuspendFunctionGun ([KTOR-3178](https://youtrack.jetbrains.com/issue/KTOR-3178))
* Support 2.0.0 in IDE ([KTOR-3196](https://youtrack.jetbrains.com/issue/KTOR-3196))
* Client HttpCache feature is not documented ([KTOR-1279](https://youtrack.jetbrains.com/issue/KTOR-1279))
* Update server dependencies and imports in docs for 2.0.0 ([KTOR-3150](https://youtrack.jetbrains.com/issue/KTOR-3150))
* Prioritize text found in feature titles over descriptions ([KTOR-2488](https://youtrack.jetbrains.com/issue/KTOR-2488))
* SerializationException when serializing request body object of generic class type ([KTOR-1019](https://youtrack.jetbrains.com/issue/KTOR-1019))
* The 'Create test for Ktor module' intention actions changes files from other modules for a multimodule Gradle project ([KTOR-3062](https://youtrack.jetbrains.com/issue/KTOR-3062))
* Wizard Plugin listing strange link ([KTOR-2882](https://youtrack.jetbrains.com/issue/KTOR-2882))
* Nothing happens when no test routes is selected when generating Ktor test for module ([KTOR-3095](https://youtrack.jetbrains.com/issue/KTOR-3095))
* The 'Create test for Ktor module' intention action doesn't create any tests if routes are defined inside the extension function ([KTOR-3079](https://youtrack.jetbrains.com/issue/KTOR-3079))
* Allow using the client itself inside Auth plugin in the refreshTokens lambda. ([KTOR-2977](https://youtrack.jetbrains.com/issue/KTOR-2977))
* ADE at io.ktor.ide.plugins.add.KtorMarketplacePluginsUpdater.checkForUpdates ([KTOR-3076](https://youtrack.jetbrains.com/issue/KTOR-3076))
* Ktor Client JS: request to /example requests http://localhost/example ([KTOR-453](https://youtrack.jetbrains.com/issue/KTOR-453))
* URLBuilder: Move Default Values to build() function ([KTOR-1345](https://youtrack.jetbrains.com/issue/KTOR-1345))
* Impossible to modify response headers ([KTOR-2822](https://youtrack.jetbrains.com/issue/KTOR-2822))
* Missing Locations params result in 404 instead of 400 ([KTOR-447](https://youtrack.jetbrains.com/issue/KTOR-447))
* Should return 405 when route exists but not for given method instead of 404 ([KTOR-737](https://youtrack.jetbrains.com/issue/KTOR-737))
* Fix 2.0.0 branch compilation ([KTOR-2603](https://youtrack.jetbrains.com/issue/KTOR-2603))
* Query of pre-signed URL has been altered after decode and re-encode process ([KTOR-778](https://youtrack.jetbrains.com/issue/KTOR-778))
* ApplicationCall.locationOrNull raises error ([KTOR-1684](https://youtrack.jetbrains.com/issue/KTOR-1684))
* Make `body` nullable for request builder ([KTOR-1400](https://youtrack.jetbrains.com/issue/KTOR-1400))
* Provide better support for Ktor clients ([KTOR-883](https://youtrack.jetbrains.com/issue/KTOR-883))
* Retry on HttpCode or network error ([KTOR-572](https://youtrack.jetbrains.com/issue/KTOR-572))
* HttpRequestRetry in KTOR 2.0 should allow for request altering between retries ([KTOR-3544](https://youtrack.jetbrains.com/issue/KTOR-3544))
* HttpCookies: parse / in the name of a cookie ([KTOR-3497](https://youtrack.jetbrains.com/issue/KTOR-3497))
* Support for adding values to the MDC later on in the pipeline. ([KTOR-536](https://youtrack.jetbrains.com/issue/KTOR-536))
* Pull Request - fix #1970 - update MultiPartFormDataContent to allow contentType override using optional builder ([KTOR-1833](https://youtrack.jetbrains.com/issue/KTOR-1833))
* Pull Request - KTOR-1264 - Add UUID to DefaultConversionService ([KTOR-1815](https://youtrack.jetbrains.com/issue/KTOR-1815))
* Pull Request - Intercept pipeline at Setup phase for XForwardedHeaderSupport feature… ([KTOR-1844](https://youtrack.jetbrains.com/issue/KTOR-1844))
* Pull Request - Add locale to ThymeleafContent ([KTOR-1838](https://youtrack.jetbrains.com/issue/KTOR-1838))
* Pull Request - KTOR-404 Introduce support for X-Http-Method-Override ([KTOR-1825](https://youtrack.jetbrains.com/issue/KTOR-1825))
* Objections to changing boundary to internal on MultiPartFormDataContent? ([KTOR-325](https://youtrack.jetbrains.com/issue/KTOR-325))
* Prototype anchors in new plugins API ([KTOR-3392](https://youtrack.jetbrains.com/issue/KTOR-3392))
* Rename the 'Ios' client engine to more generic term to cover all Apple operating systems ([KTOR-3394](https://youtrack.jetbrains.com/issue/KTOR-3394))
* Update Samples to Ktor 2.0 ([KTOR-3218](https://youtrack.jetbrains.com/issue/KTOR-3218))
* Implement new `Locations` feature ([KTOR-1706](https://youtrack.jetbrains.com/issue/KTOR-1706))
* Feature: Use websockets with serialization ([KTOR-423](https://youtrack.jetbrains.com/issue/KTOR-423))
* JS Client doesn't support ServiceWorker ([KTOR-3448](https://youtrack.jetbrains.com/issue/KTOR-3448))
* Move Server Related Code from `ktor-http-cio` to `ktor-server-cio` ([KTOR-3462](https://youtrack.jetbrains.com/issue/KTOR-3462))
* Client request builder: add shortcuts for authentication headers ([KTOR-2876](https://youtrack.jetbrains.com/issue/KTOR-2876))
* Add Check if Feature is installed for `WebSocket` builders ([KTOR-3459](https://youtrack.jetbrains.com/issue/KTOR-3459))
* Implementation for Create `ktor-test` module with mocks of engine and clients for writing tests ([KTOR-3236](https://youtrack.jetbrains.com/issue/KTOR-3236))
* TestEngineApplication - implement HttpClient API ([KTOR-2416](https://youtrack.jetbrains.com/issue/KTOR-2416))
* Add explicit menu action for migration ([KTOR-3400](https://youtrack.jetbrains.com/issue/KTOR-3400))
* Add possibility to fully configure metricName in ktor-server-metrics-micrometer ([KTOR-3302](https://youtrack.jetbrains.com/issue/KTOR-3302))
* Support New Native Memory Model ([KTOR-3217](https://youtrack.jetbrains.com/issue/KTOR-3217))
* Server for Kotlin Native ([KTOR-746](https://youtrack.jetbrains.com/issue/KTOR-746))
* call.request.queryParameters decode plus as space ([KTOR-3297](https://youtrack.jetbrains.com/issue/KTOR-3297))
* Migrate existing plugins to RoutingScoped ([KTOR-3201](https://youtrack.jetbrains.com/issue/KTOR-3201))
* Support receiving OAuth code response as form post ([KTOR-3342](https://youtrack.jetbrains.com/issue/KTOR-3342))
* Add parameter for specifying content-length in ApplicationCall#respondBytes ([KTOR-3087](https://youtrack.jetbrains.com/issue/KTOR-3087))
* Allow application environment configuration when running via commandLineEnvironment ([KTOR-3027](https://youtrack.jetbrains.com/issue/KTOR-3027))
* Add locale to ThymeleafContent ([KTOR-3313](https://youtrack.jetbrains.com/issue/KTOR-3313))
* Add support for ports in withTestApplication ([KTOR-725](https://youtrack.jetbrains.com/issue/KTOR-725))
* Expose non-reified request methods ([KTOR-2590](https://youtrack.jetbrains.com/issue/KTOR-2590))
* XML Support in Ktor ([KTOR-489](https://youtrack.jetbrains.com/issue/KTOR-489))
* Start ktor server on random port ([KTOR-686](https://youtrack.jetbrains.com/issue/KTOR-686))
* parameterOf() should have a variant that takes in a Map<String, List<String>> ([KTOR-399](https://youtrack.jetbrains.com/issue/KTOR-399))
* [Ktor Client] CborFeature ([KTOR-3174](https://youtrack.jetbrains.com/issue/KTOR-3174))
* Can't set a base url that includes path data ([KTOR-730](https://youtrack.jetbrains.com/issue/KTOR-730))
* Ktor: Fold internal stack frames for HTTP server ([KTOR-2274](https://youtrack.jetbrains.com/issue/KTOR-2274))
* Add method to Client and ServerResponseException ([KTOR-3128](https://youtrack.jetbrains.com/issue/KTOR-3128))
* Add UUID to DefaultConversionService ([KTOR-1264](https://youtrack.jetbrains.com/issue/KTOR-1264))
* Implementation for Simple API for writing features ([KTOR-2480](https://youtrack.jetbrains.com/issue/KTOR-2480))
* Add filtering support in Ktor client response interceptor ([KTOR-2992](https://youtrack.jetbrains.com/issue/KTOR-2992))
* Define completion priorities for Ktor keywords ([KTOR-2773](https://youtrack.jetbrains.com/issue/KTOR-2773))
* Adding features action in IDE ([KTOR-2893](https://youtrack.jetbrains.com/issue/KTOR-2893))
* Implement design about moving features from ktor-server-core ([KTOR-1239](https://youtrack.jetbrains.com/issue/KTOR-1239))
* Move server code to io.ktor.server.* package ([KTOR-2865](https://youtrack.jetbrains.com/issue/KTOR-2865))
* ApplicationConfig: how to iterate over keys and values of config ([KTOR-2318](https://youtrack.jetbrains.com/issue/KTOR-2318))
* Implementation for Events Feature For Client Metrics ([KTOR-2472](https://youtrack.jetbrains.com/issue/KTOR-2472))
* ContentConverter.convertForSend should receive a KType ([KTOR-444](https://youtrack.jetbrains.com/issue/KTOR-444))
* Send 100 Continue response only when getting a request to receive `IncomingContent` ([KTOR-855](https://youtrack.jetbrains.com/issue/KTOR-855))
* Update Documentation and Code for HttpsRedirect Feature ([KTOR-1879](https://youtrack.jetbrains.com/issue/KTOR-1879))
* Improve documentation for native/Apple client engines ([KTOR-3375](https://youtrack.jetbrains.com/issue/KTOR-3375))
* Update Documentation and Code for CallId Feature ([KTOR-1874](https://youtrack.jetbrains.com/issue/KTOR-1874))
* Feature to Plugin changes in Documentation ([KTOR-2372](https://youtrack.jetbrains.com/issue/KTOR-2372))

# 1.6.7
> Published 6 December 2021

* Explicitly specify jdk version for building ([KTOR-3358](https://youtrack.jetbrains.com/issue/KTOR-3358))
* Make URL constructor public again ([KTOR-3514](https://youtrack.jetbrains.com/issue/KTOR-3514))

# 1.6.6
> Published 25 November 2021
* Some Netty EngineMain properties are not set ([KTOR-3464](https://youtrack.jetbrains.com/issue/KTOR-3464))
* Session cookie with BASE64 encoding fails to set correct cookie ([KTOR-524](https://youtrack.jetbrains.com/issue/KTOR-524))
* corsCheckRequestHeaders false ([KTOR-445](https://youtrack.jetbrains.com/issue/KTOR-445))
* DropwizardMetrics does not append baseName to the 'per endpoint'-metrics ([KTOR-2527](https://youtrack.jetbrains.com/issue/KTOR-2527))
* Cookies that added to request got removed if HttpCookies plugin is installed ([KTOR-3105](https://youtrack.jetbrains.com/issue/KTOR-3105))
* Development mode isn't taken into account for subroutes ([KTOR-3316](https://youtrack.jetbrains.com/issue/KTOR-3316))
* URL port should be in 0..65535 ([KTOR-3314](https://youtrack.jetbrains.com/issue/KTOR-3314))
* Basic auth not sending second request ([KTOR-3472](https://youtrack.jetbrains.com/issue/KTOR-3472))
* Update Kotlin to 1.6.0 ([KTOR-3422](https://youtrack.jetbrains.com/issue/KTOR-3422))


# 1.6.5
> Published 2 November 2021

* Bump kotlin from 1.5.30 to 1.5.31
* Bump tomcat from 9.0.58 to 9.0.54
* Bump logback from 1.2.3 to 1.2.6
* Bump slf4j from 1.7.30 to 1.7.32
* Bump gson from 2.8.6 to 2.8.9
* Bump okhttp from 4.6.0 to 4.9.2
* Bump jackson from 2.12.3 to 2.13.0
* Bump mockk from 1.10.6 to 1.12.0
* Add Apple Silicon targets ([KTOR-3082](https://youtrack.jetbrains.com/issue/KTOR-3082))
* Fix HttpCookies feature overwriting request cookies ([KTOR-3105](https://youtrack.jetbrains.com/issue/KTOR-3105))
* Change EAP version scheme ([KTOR-3319](https://youtrack.jetbrains.com/issue/KTOR-3319))
* Update Netty to 4.1.69.Final ([KTOR-472](https://youtrack.jetbrains.com/issue/KTOR-472))
* Allow wildcard origins for CORS requests ([KTOR-316](https://youtrack.jetbrains.com/issue/KTOR-316))
* Add a host check for illegal symbols ([KTOR-384](https://youtrack.jetbrains.com/issue/KTOR-384))
* Add check to prevent anyHost with allowCredentials ([KTOR-2872](https://youtrack.jetbrains.com/issue/KTOR-2872))
* Bump metrics-core from 4.2.3 to 4.2.4
* Bump webjars-locator-core from 0.47 to 0.48
* Bump metrics-jvm from 4.2.3 to 4.2.4
* Fix ProxyType.SOCKS being mapped to Proxy.Type.DIRECT
* fix grammar ([KTOR-3237](https://youtrack.jetbrains.com/issue/KTOR-3237))
* Bump micrometer-core from 1.7.4 to 1.7.5
* Ignore flaky testTimeoutPriority ([KTOR-3243](https://youtrack.jetbrains.com/issue/KTOR-3243))
* Fix npe if static file not found ([KTOR-2811](https://youtrack.jetbrains.com/issue/KTOR-2811))
* Fix flaky timeoutPriorityTest ([KTOR-3243](https://youtrack.jetbrains.com/issue/KTOR-3243))
* Fill Content-Length for PartialContent ([KTOR-308](https://youtrack.jetbrains.com/issue/KTOR-308))
* Change default log-level to INFO ([KTOR-806](https://youtrack.jetbrains.com/issue/KTOR-806))
* Use require from the stdlib instead of internal require ([KTOR-2626](https://youtrack.jetbrains.com/issue/KTOR-2626))

# 1.6.4
> Published 30 September 2021

* [Auth] [Interceptors] Phase Phase('Challenge') was not registered for this pipeline ([KTOR-3156](https://youtrack.jetbrains.com/issue/KTOR-3156))
* insertPhaseBefore and insertPhaseAfter lead to different order ([KTOR-438](https://youtrack.jetbrains.com/issue/KTOR-438))
* Ktor 1.6.3 crashes on restart due to java.lang.ClassNotFoundException: Didn't find class "java.nio.file.WatchService" on Android 24 ([KTOR-3166](https://youtrack.jetbrains.com/issue/KTOR-3166))
* GraalVM binary using CIO fails on start "Module function cannot be found" ([KTOR-2987](https://youtrack.jetbrains.com/issue/KTOR-2987))
* Logging in Shutdown thread looks not informative ([KTOR-3175](https://youtrack.jetbrains.com/issue/KTOR-3175))
* Installed Closeable features not closed when closing HttpClient ([KTOR-3116](https://youtrack.jetbrains.com/issue/KTOR-3116))
* Explain method(HttpMethod.Options) in docs for CORS ([KTOR-2913](https://youtrack.jetbrains.com/issue/KTOR-2913))
* ContentType.parse("text/html qqq") must fail with error ([KTOR-3080](https://youtrack.jetbrains.com/issue/KTOR-3080))
* Update JSON topics using code snippets from the 'codeSnippets' project ([KTOR-2955](https://youtrack.jetbrains.com/issue/KTOR-2955))
* Could not find artifact org.jetbrains.kotlinx:kotlinx-html-jvm:pom:0.7.2 ([KTOR-2481](https://youtrack.jetbrains.com/issue/KTOR-2481))
* Update the 'Modules' topic ([KTOR-1861](https://youtrack.jetbrains.com/issue/KTOR-1861))
* Native engines tests are not run outside of the ` ktor-client-tests` module ([KTOR-3069](https://youtrack.jetbrains.com/issue/KTOR-3069))
* MultiPartData.readAllParts throws IOException when the epilogue is omitted ([KTOR-3173](https://youtrack.jetbrains.com/issue/KTOR-3173))
* Update Kotlin and Coroutines Versions ([KTOR-3103](https://youtrack.jetbrains.com/issue/KTOR-3103))

# 1.6.3
> Published 26 August 2021

* Auth Feature: token refresh works only on main thread in Kotlin/Native ([KTOR-3055](https://youtrack.jetbrains.com/issue/KTOR-3055))
* FUS report mixes up feature id and feature version ([KTOR-3067](https://youtrack.jetbrains.com/issue/KTOR-3067))
* SessionTrackerById - doesn't remove invalid session id ([KTOR-2584](https://youtrack.jetbrains.com/issue/KTOR-2584))
* Bearer Token is Not Initialized after Clean ([KTOR-3008](https://youtrack.jetbrains.com/issue/KTOR-3008))
* ktor does not support semicolon query parameter in Netty Engine ([KTOR-2991](https://youtrack.jetbrains.com/issue/KTOR-2991))
* HOCON config not resolved in ServletApplicationEngine ([KTOR-3020](https://youtrack.jetbrains.com/issue/KTOR-3020))
* Deploy WAR on Tomcat ([KTOR-2867](https://youtrack.jetbrains.com/issue/KTOR-2867))
* The wizard missing the Pebble plugin ([KTOR-2922](https://youtrack.jetbrains.com/issue/KTOR-2922))
* Wizard: Creating a project without sample code creates `Application.configureRouting` without `routing` ([KTOR-2581](https://youtrack.jetbrains.com/issue/KTOR-2581))
* The 'Create Run Configuration automatically' option name is cropped ([KTOR-2898](https://youtrack.jetbrains.com/issue/KTOR-2898))
* InvalidMutabilityException when using withContext and SavedHttpCall ([KTOR-2033](https://youtrack.jetbrains.com/issue/KTOR-2033))
* Reuse Package Search to add dependencies for Ktor Features in Plugin ([KTOR-2433](https://youtrack.jetbrains.com/issue/KTOR-2433))
* Server: TLSConfigBuilder.addKeyStore: store.getCertificateChain could return null([KTOR-3047](https://youtrack.jetbrains.com/issue/KTOR-3047))


# 1.6.2
> Published 29 July 2021

* Fixed Ktor plugin raises StackOverflowError when opening some files ([KTOR-2950](https://youtrack.jetbrains.com/issue/KTOR-2950))
* Added parseUrlEncodedParameters Documentation ([KTOR-2843](https://youtrack.jetbrains.com/issue/KTOR-2843))
* Fixed CIO WebSockets client incorrectly sends Sec-WebSocket-Extensions header even if empty regression ([KTOR-2388](https://youtrack.jetbrains.com/issue/KTOR-2388))
* Updated serialization version to 1.2.2 ([KTOR-2968](https://youtrack.jetbrains.com/issue/KTOR-2968))
* Made code example complete in OAuth documentation([KTOR-1415](https://youtrack.jetbrains.com/issue/KTOR-1415))
* Added quick action on a Application.module(...) to generate tests for a given module with all the endpoints in plugin ([KTOR-2411](https://youtrack.jetbrains.com/issue/KTOR-2411))
* Fixed kotlin.native.concurrent.InvalidMutabilityException: mutation attempt of frozen <object>@72c18 ([KTOR-2883](https://youtrack.jetbrains.com/issue/KTOR-2883))
* Fixed 404 errors in ktor docs ([KTOR-2915](https://youtrack.jetbrains.com/issue/KTOR-2915))
* Added prometeus version to Ktor docs ([KTOR-2015](https://youtrack.jetbrains.com/issue/KTOR-2015))
* Fixed incorrect Structured Markup (LD+JSON) on Ktor docs ([KTOR-2943](https://youtrack.jetbrains.com/issue/KTOR-2943))
* Fixed confusing/incorrect JWT auth documentation ([KTOR-979](https://youtrack.jetbrains.com/issue/KTOR-979))
* Fixed embedded Netty Server with watch paths is crashing in API level 22 when calling stopping server ([KTOR-1613](https://youtrack.jetbrains.com/issue/KTOR-1613))
* Fixed NoSuchMethodError: No virtual method getParameterCount on Android API 25 and lower regression ([KTOR-2924](https://youtrack.jetbrains.com/issue/KTOR-2924))
* Fixed X-Forwarded-Port Parse Exception when it contains comma separated list of ports regression ([KTOR-2918](https://youtrack.jetbrains.com/issue/KTOR-2918))
* Made CookieConfiguration default to secure configuration and require user opt-out long-standing ([KTOR-628](https://youtrack.jetbrains.com/issue/KTOR-628))
* Updated docs section about testing with cookies ([KTOR-273](https://youtrack.jetbrains.com/issue/KTOR-273))
* Fixed "ApplicationEngineEnvironment was not started" when accessing application before server is started ([KTOR-1854](https://youtrack.jetbrains.com/issue/KTOR-1854))
* Updated HTTP/2 documentation ([KTOR-267](https://youtrack.jetbrains.com/issue/KTOR-267))
* Fixed NPE in ApacheRequestProducer when "http://" is requested ([KTOR-1405](https://youtrack.jetbrains.com/issue/KTOR-1405))
* Updated vulnerable versions from sonatype report ([KTOR-2875](https://youtrack.jetbrains.com/issue/KTOR-2875))
* Fixed ByteReadChannel.readUTF8Line() indefinitely returns empty lines when `\r` is not followed by `\n` ([KTOR-2868](https://youtrack.jetbrains.com/issue/KTOR-2868))


# 1.6.1
> Published 1 July 2021

* Linked back to site from Docs ([KTOR-2843](https://youtrack.jetbrains.com/issue/KTOR-2843))
* Fixed unbound public symbol for public io.ktor.network.sockets/SocketTimeoutException when iosArm64 framework ([KTOR-2276](https://youtrack.jetbrains.com/issue/KTOR-2276))
* Fixed configureBootstrap hook overwritten by Ktor settings ([KTOR-356](https://youtrack.jetbrains.com/issue/KTOR-356))
* Fixed crypto is undefined in IE11 ([KTOR-409](https://youtrack.jetbrains.com/issue/KTOR-409))
* Added support for X-Forwarded-Port header in XForwardedHeaderSupport plugin ([KTOR-2788](https://youtrack.jetbrains.com/issue/KTOR-2788))
* Fixed StatusPages doesn't catch FreeMarker exceptions ([KTOR-343](https://youtrack.jetbrains.com/issue/KTOR-343))
* Fixed java.nio.charset.IllegalCharsetNameException: %s ([KTOR-2645](https://youtrack.jetbrains.com/issue/KTOR-2645))
* Added application startup and hot-reloading time log ([KTOR-2816](https://youtrack.jetbrains.com/issue/KTOR-2816))
* Fixed postpone (and don't cache) name resolution in cio client ([KTOR-2513](https://youtrack.jetbrains.com/issue/KTOR-2513))
* Improved diagnostics for exceptions inherited from IOException ([KTOR-2691](https://youtrack.jetbrains.com/issue/KTOR-2691))
* Fixed refresh token gets stuck ([KTOR-2797](https://youtrack.jetbrains.com/issue/KTOR-2797))
* Fixed developmentMode is on by default in tests ([KTOR-2727](https://youtrack.jetbrains.com/issue/KTOR-2727))
* Fixed unable to run new Ktor project ([KTOR-2586](https://youtrack.jetbrains.com/issue/KTOR-2586))
* Fixed unhandled get freezes with `CIO` server ([KTOR-333](https://youtrack.jetbrains.com/issue/KTOR-333))
* Fixed double host header ([KTOR-379](https://youtrack.jetbrains.com/issue/KTOR-379))
* Fixed use kotlin.reflect.jvm.javaType instead of the type token pattern in io.ktor.util.reflect.typeInfo ([KTOR-2709](https://youtrack.jetbrains.com/issue/KTOR-2709))
* Fixed "JWK Public Key of type ""EC""" ([KTOR-2387](https://youtrack.jetbrains.com/issue/KTOR-2387))
* Fixed lots of Run Configurations Created for Ktor Project with the Similar Names ([KTOR-2803](https://youtrack.jetbrains.com/issue/KTOR-2803))
* Fixed ApplicationEngineEnvironmentBuilder.module { … } is executed twice on Exception ([KTOR-2734](https://youtrack.jetbrains.com/issue/KTOR-2734))
* Researched shared indexes for Ktor ([KTOR-2774](https://youtrack.jetbrains.com/issue/KTOR-2774))
* Fixed selecting custom package name in Ktor wizard still results in example.com import in ApplicationTest.kt ([KTOR-2707](https://youtrack.jetbrains.com/issue/KTOR-2707))
* Fixed generated project with specific security and session features selected fails to compile / run ([KTOR-2636](https://youtrack.jetbrains.com/issue/KTOR-2636))
* Fixed Wizard: Misleading comment in Static Feature ([KTOR-2560](https://youtrack.jetbrains.com/issue/KTOR-2560))
* Fixed "Update ktor 1.5.0 docs. Deprecated ""challenge"" function for form auth in docs." ([KTOR-1974](https://youtrack.jetbrains.com/issue/KTOR-1974))
* Fixed Auth Feature Code Snippet: form authentication the doesn't work ([KTOR-821](https://youtrack.jetbrains.com/issue/KTOR-821))
* Fixed the '-ea' flag works differently when running a server using Application.module and embeddedServer ([KTOR-1758](https://youtrack.jetbrains.com/issue/KTOR-1758))
* Fixed enabled-by-default development mode breaks reflection by overriding classloader ([KTOR-2306](https://youtrack.jetbrains.com/issue/KTOR-2306))
* Reviewed documentation for the onUpload/onDownload client callbacks ([KTOR-2710](https://youtrack.jetbrains.com/issue/KTOR-2710))
* Fixed Ktor fails to deliver response with error: failed with exception: kotlinx.coroutines.JobCancellationException: Parent job is Completed; ([KTOR-2711](https://youtrack.jetbrains.com/issue/KTOR-2711))
* Replaced kotlin-test dependency with junit in ktor-server-test-host ([KTOR-2555](https://youtrack.jetbrains.com/issue/KTOR-2555))

# 1.6.0
> Published 28 May 2021

* Ktor fails to deliver response with error: failed with exception: kotlinx.coroutines.JobCancellationException: Parent job is Completed; ([KTOR-2711](https://youtrack.jetbrains.com/issue/KTOR-2711))
* Wrong Tabs Name in Code Blocks ([KTOR-2726](https://youtrack.jetbrains.com/issue/KTOR-2726))
* Apache HTTP Client does not send Content-Length header if body is empty content ([KTOR-556](https://youtrack.jetbrains.com/issue/KTOR-556))
* Review Auth providers ([KTOR-2637](https://youtrack.jetbrains.com/issue/KTOR-2637))
* When the main thread executes runBlocking, using the iOS engine will cause a deadlock ([KTOR-2683](https://youtrack.jetbrains.com/issue/KTOR-2683))
* Deprecate TestApplicationCall.requestHandled ([KTOR-2712](https://youtrack.jetbrains.com/issue/KTOR-2712))
* Update Dokka: Dokka tasks fails with old dokka version and Gradle 7 ([KTOR-2693](https://youtrack.jetbrains.com/issue/KTOR-2693))
* Duplicate server `Features` Section on the Documentation Website ([KTOR-2702](https://youtrack.jetbrains.com/issue/KTOR-2702))
* Duplicate entry "Features" in Server docs ([KTOR-1546](https://youtrack.jetbrains.com/issue/KTOR-1546))
* Upgrading from 1.4.3 to 1.5.2 introduced a routing precedence ([KTOR-2278](https://youtrack.jetbrains.com/issue/KTOR-2278))
* Sporadic OkHttp errors after upgrading to ktor 1.3.1 ([KTOR-449](https://youtrack.jetbrains.com/issue/KTOR-449))
* Netty: server freezes after start error ([KTOR-803](https://youtrack.jetbrains.com/issue/KTOR-803))
* aSocket().bind() sometimes throws Already bound SocketException ([KTOR-638](https://youtrack.jetbrains.com/issue/KTOR-638))
* UDPSocketTest.testBroadcastSuccessful[jvm] is failing ([KTOR-2616](https://youtrack.jetbrains.com/issue/KTOR-2616))
* Fix flaky CIOHttpsTest.customDomainsTest[jvm] ([KTOR-2065](https://youtrack.jetbrains.com/issue/KTOR-2065))
* Occasionally empty response using Netty + Jackson ([KTOR-1973](https://youtrack.jetbrains.com/issue/KTOR-1973))
* '%3D' inside query of redirect target location will be replaced to '=' ([KTOR-2057](https://youtrack.jetbrains.com/issue/KTOR-2057))
* CIO: TLSConfigBuilder JVM allow null as password ([KTOR-940](https://youtrack.jetbrains.com/issue/KTOR-940))
* route("{...}") stopped matching root ([KTOR-1965](https://youtrack.jetbrains.com/issue/KTOR-1965))
* call.respond() will not check or apply ContentNegotiation for some types ([KTOR-2194](https://youtrack.jetbrains.com/issue/KTOR-2194))
* Add support for Velocity Tools ([KTOR-2345](https://youtrack.jetbrains.com/issue/KTOR-2345))
* Base name of micrometer metrics is not configurable ([KTOR-2210](https://youtrack.jetbrains.com/issue/KTOR-2210))
* Support for Compression Extensions for WebSocket (RFC 7692) ([KTOR-688](https://youtrack.jetbrains.com/issue/KTOR-688))
* Document usage of Bearer token in Http Client ([KTOR-2439](https://youtrack.jetbrains.com/issue/KTOR-2439))
* How to track leaked buffers in ktor-io? ([KTOR-2442](https://youtrack.jetbrains.com/issue/KTOR-2442))
* Routing: Add PutTyped and PatchTyped Overload ([KTOR-1344](https://youtrack.jetbrains.com/issue/KTOR-1344))
* Migrate to Dokka 1.4.0 ([KTOR-1032](https://youtrack.jetbrains.com/issue/KTOR-1032))
* Client upload/download progress observer/handler/interceptor ([KTOR-400](https://youtrack.jetbrains.com/issue/KTOR-400))
* HTTP-client auth with Bearer token ([KTOR-331](https://youtrack.jetbrains.com/issue/KTOR-331))
* Expose TrailingSlashRouteSelector ([KTOR-2511](https://youtrack.jetbrains.com/issue/KTOR-2511))
* Add an option to disable URL Encoding ([KTOR-553](https://youtrack.jetbrains.com/issue/KTOR-553))
* Upgrade kotlin to 1.5.10 ([KTOR-2722](https://youtrack.jetbrains.com/issue/KTOR-2722))

# 1.5.4
> Published 30 Apr 2021

* Fixed extra trailing slashes in Route.toString ([KTOR-2427](https://youtrack.jetbrains.com/issue/KTOR-2427))
* Fixed ByteReadChannel.read related issues ([KTOR-2615](https://youtrack.jetbrains.com/issue/KTOR-2516),
  [KTOR-2519](https://youtrack.jetbrains.com/issue/KTOR-2519))
* Fixed silently ignored exceptions in HTML DSL with StatusPages feature ([KTOR-756](https://youtrack.jetbrains.com/issue/KTOR-756))
* Changed IosHttpRequestException supertype to IOException ([KTOR-2566](https://youtrack.jetbrains.com/issue/KTOR-2566))
* Fixed utility collection implementation for K/N ([KTOR-2482](https://youtrack.jetbrains.com/issue/KTOR-2482))
* Fixed client Digest auth realm handling ([KTOR-1464](https://github.com/ktorio/ktor/pull/2347))

# 1.5.3
> Published 2 Apr 2021

*  Upgraded to coroutines 1.4.3 ([KTOR-2254](https://youtrack.jetbrains.com/issue/KTOR-2254))
*  Upgraded kotlinx.serialization to 1.1.0 ([KTOR-2238](https://youtrack.jetbrains.com/issue/KTOR-2238))
*  Fixed I/O readRemaining sometimes looses exception ([KTOR-2263](https://youtrack.jetbrains.com/issue/KTOR-2263))
*  Fixed autoreload with 1.5.x when using embeddedServer NOT in debug mode regression  ([KTOR-2214](https://youtrack.jetbrains.com/issue/KTOR-2214))
*  Fixed flaky CIOSustainabilityTest.testBlockingConcurrency[jvm] ([KTOR-2265](https://youtrack.jetbrains.com/issue/KTOR-2265))
*  Resolve 'node-fetch' on libs produced by jsBrowserProductionLibraryDistribution regression  ([KTOR-2230](https://youtrack.jetbrains.com/issue/KTOR-2230))
*  Updated doc string for FormPart ([KTOR-2173](https://youtrack.jetbrains.com/issue/KTOR-2173))
*  Fixed java.lang.IllegalStateException: No instance for key AttributeKey: ExpectSuccessAttribyteKey regression  ([KTOR-2389](https://youtrack.jetbrains.com/issue/KTOR-2389))
*  Supported overriding Kotlin module configuration using jackson dsl function ([KTOR-1692](https://youtrack.jetbrains.com/issue/KTOR-1692))
*  Fixed CORS can't pass on some none standard orgin on jvm  ([KTOR-469](https://youtrack.jetbrains.com/issue/KTOR-469))
*  Fixed unexpected exception when using Session feature: "Using blocking primitives on this dispatcher is not allowed" regression jvm  ([KTOR-1452](https://youtrack.jetbrains.com/issue/KTOR-1452))
*  Fixed NettyApplicationEngine providing a configureBootstrap in the configuration throws IllegalStateException: group set already ([KTOR-2078](https://youtrack.jetbrains.com/issue/KTOR-2078))
*  Fixed wrong indentation in `Serving Static Content` guide ([KTOR-2017](https://youtrack.jetbrains.com/issue/KTOR-2017))
*  Fixed InsufficientSpaceException trying to build ByteReadPacket jvm  ([KTOR-960](https://youtrack.jetbrains.com/issue/KTOR-960))
*  Fixed flaky ProxyTest.testHttpProxy[CIO][jvm] ([KTOR-2082](https://youtrack.jetbrains.com/issue/KTOR-2082))
*  Fixed invalid assertion for existence of the key in the key store ([KTOR-2311](https://youtrack.jetbrains.com/issue/KTOR-2311))
*  Fixed incorrect grammar in exception messages ([KTOR-2284](https://youtrack.jetbrains.com/issue/KTOR-2284))
*  Fixed flaky JavaEngineTests.testThreadLeak[jvm] ([KTOR-2098](https://youtrack.jetbrains.com/issue/KTOR-2098))
*  Fixed flaky JettyStressTest.highLoadStressTest ([KTOR-2080](https://youtrack.jetbrains.com/issue/KTOR-2080))
*  Fixed flaky ExceptionsJvmTest.testConnectionClosedDuringRequest[jvm] ([KTOR-2063](https://youtrack.jetbrains.com/issue/KTOR-2063))
* Upgrade kotlin to 1.4.32 ([KTOR-2403](https://youtrack.jetbrains.com/issue/KTOR-2403))

# 1.5.2
> Published 25 Feb 2021

* Fixed Dokka building for master ([KTOR-2206](https://youtrack.jetbrains.com/issue/KTOR-2206))
* Fixed native build on linux machine ([KTOR-2200](https://youtrack.jetbrains.com/issue/KTOR-2200))
* Fixed docker doc is incorrect / does not work ([KTOR-2179](https://youtrack.jetbrains.com/issue/KTOR-2179))
* Fixed crash with Firebase Performance in iOS ([KTOR-642](https://youtrack.jetbrains.com/issue/KTOR-642))
* Fixed Ktor Client CIO engine Jvm ignores Cipher suites with key strength more than 128 bits. ([KTOR-1914](https://youtrack.jetbrains.com/issue/KTOR-1914))
* Fixed mandatory Path Segment parameter can be empty, if no explicit route with trailing / is defined ([KTOR-2054](https://youtrack.jetbrains.com/issue/KTOR-2054))
* Fixed flaky ClientSocketTest.testSelfConnect[jvm] ([KTOR-2060](https://youtrack.jetbrains.com/issue/KTOR-2060))
* Switch JS Fetch API to Standard Library (org.w3c.fetch.*) ([KTOR-1460](https://youtrack.jetbrains.com/issue/KTOR-1460))
* Fixed CIO server always start on "0.0.0.0" - does not respect "connector" configuration ([KTOR-334](https://youtrack.jetbrains.com/issue/KTOR-334))
* Fixed server/netty: IllegalReferenceCountException ([KTOR-1801](https://youtrack.jetbrains.com/issue/KTOR-1801))
* Fixed digest authentication: cannot successfully pass authentication using curl or web browser ([KTOR-1466](https://youtrack.jetbrains.com/issue/KTOR-1466))
* Fixed HTTP Client exception is masked by JobCancellationException with Ktor 1.5.0 ([KTOR-1967](https://youtrack.jetbrains.com/issue/KTOR-1967))
* Fixed changing `requestTimeoutMillis` in config of HttpTimeout feature doesn't change the CIO's timeout ([KTOR-2000](https://youtrack.jetbrains.com/issue/KTOR-2000))
* Fixed test a POST with MultiPart using TestApplicationEngine does not success or fail ([KTOR-345](https://youtrack.jetbrains.com/issue/KTOR-345))
* Fixed default Headers feature adds duplicated Server header ([KTOR-1976](https://youtrack.jetbrains.com/issue/KTOR-1976))
* Fixed custom response validation is not running when default is disabled ([KTOR-2007](https://youtrack.jetbrains.com/issue/KTOR-2007))
* Fixed session cookie with very long max age duration ([KTOR-692](https://youtrack.jetbrains.com/issue/KTOR-692))
* Upgrade kotlin to 1.4.30 ([KTOR-1639](https://youtrack.jetbrains.com/issue/KTOR-1639))

# 1.5.1
> Published 27 Jan 2021

* Circular reference for SocketException and StackOverflowError when using SLF4J logger ([KTOR-1080](https://youtrack.jetbrains.com/issue/KTOR-1080))
* start.ktor.io - Incorrect import for websockets for ktor 1.2.4 ([KTOR-274](https://youtrack.jetbrains.com/issue/KTOR-274))
* Unable to catch socket exceptions ([KTOR-1166](https://youtrack.jetbrains.com/issue/KTOR-1166))
* Support explicit WebSocket session close ([KTOR-340](https://youtrack.jetbrains.com/issue/KTOR-340))
* ktor-client-apache: thread stuck in ByteBufferChannel.readRemainingSuspend ([KTOR-1463](https://youtrack.jetbrains.com/issue/KTOR-1463))
* Logging tests fails due to floating log entries ([KTOR-1870](https://youtrack.jetbrains.com/issue/KTOR-1870))
* Adding existing dropwizard metrics registry to Ktor ([KTOR-1798](https://youtrack.jetbrains.com/issue/KTOR-1798))
* Exception kotlinx.serialization.SerializationException: Class 'ArrayList' is not registered for polymorphic serialization in the scope of 'Collection' in 1.5.0 ([KTOR-1795](https://youtrack.jetbrains.com/issue/KTOR-1795))
* Prevent double quotes on header params ([KTOR-1797](https://youtrack.jetbrains.com/issue/KTOR-1797))
* Post request shows empty body after upgrading v1.3.2 ([KTOR-426](https://youtrack.jetbrains.com/issue/KTOR-426))
* CIO native selector doesn't select new descriptors ([KTOR-1856](https://youtrack.jetbrains.com/issue/KTOR-1856))
* Client logging docs don't mention all required dependencies ([KTOR-280](https://youtrack.jetbrains.com/issue/KTOR-280))
* Out of date self-signed-certificate documentation ([KTOR-272](https://youtrack.jetbrains.com/issue/KTOR-272))
* ClosedReceiveChannelException when making request with CIO engine using a proxy to https ([KTOR-1458](https://youtrack.jetbrains.com/issue/KTOR-1458))
* Incorrect encoding function used for URL path by URLBuilder ([KTOR-1543](https://youtrack.jetbrains.com/issue/KTOR-1543))
* A single slash gets ignored for defining a route, but 1.5 requires them due to KTOR-372 ([KTOR-1615](https://youtrack.jetbrains.com/issue/KTOR-1615))
* Wrong shadow plugin version in Fat JAR docs ([KTOR-1359](https://youtrack.jetbrains.com/issue/KTOR-1359))
* ktor server documentation is returning 404 ([KTOR-1602](https://youtrack.jetbrains.com/issue/KTOR-1602))
* CORS doesn't reject bad headers ([KTOR-1662](https://youtrack.jetbrains.com/issue/KTOR-1662))
* OkHTTP client engine tries to close the connection twice during the closing handshake ([KTOR-1374](https://youtrack.jetbrains.com/issue/KTOR-1374))
* Dispatcher is closing earlier than client ([KTOR-1661](https://youtrack.jetbrains.com/issue/KTOR-1661))
* Server losing channel exceptions at receive ([KTOR-1590](https://youtrack.jetbrains.com/issue/KTOR-1590))
* Request parameters should have name ([KTOR-378](https://youtrack.jetbrains.com/issue/KTOR-378))
* Status-code must be 3-digit ([KTOR-370](https://youtrack.jetbrains.com/issue/KTOR-370))
* Connect request sends wrong status line ([KTOR-1612](https://youtrack.jetbrains.com/issue/KTOR-1612))
* Response channel is always cancelled with Logging feature ([KTOR-1598](https://youtrack.jetbrains.com/issue/KTOR-1598))
* Java client logging tests are fluky ([KTOR-1599](https://youtrack.jetbrains.com/issue/KTOR-1599))
* HttpTimeoutTest.testConnect are flaky ([KTOR-1583](https://youtrack.jetbrains.com/issue/KTOR-1583))
* Jetty: requests to resources, that doesn't respond with HTTP/2, lead to unexpected behaviour ([KTOR-874](https://youtrack.jetbrains.com/issue/KTOR-874))
* "Unfinished workers detected" using client on native ([KTOR-1220](https://youtrack.jetbrains.com/issue/KTOR-1220))
* HttpTimeout.testSocketTimeoutWriteFail is flaky ([KTOR-1584](https://youtrack.jetbrains.com/issue/KTOR-1584))
* Reserved characters in path is not encoded ([KTOR-570](https://youtrack.jetbrains.com/issue/KTOR-570))
* testTimeoutCancelsWhenParentScopeCancels is flaky ([KTOR-1585](https://youtrack.jetbrains.com/issue/KTOR-1585))
* Java client freeze ([KTOR-1567](https://youtrack.jetbrains.com/issue/KTOR-1567))
* CallLoggingTest is flaky ([KTOR-1582](https://youtrack.jetbrains.com/issue/KTOR-1582))
* Missing dependency information the Authentication and Authorization topic ([KTOR-1575](https://youtrack.jetbrains.com/issue/KTOR-1575))
* "Using a Self-Signed Certificate" docs provide wrong dependency for 1.3.x ([KTOR-21](https://youtrack.jetbrains.com/issue/KTOR-21))
* "Testing Http Client" docs page contains artifact name with -native suffix ([KTOR-1006](https://youtrack.jetbrains.com/issue/KTOR-1006))
* Custom JSON mapping with Jackson ([KTOR-603](https://youtrack.jetbrains.com/issue/KTOR-603))
* Serialization for client section does not explain how to use it ([KTOR-999](https://youtrack.jetbrains.com/issue/KTOR-999))
* Add information about required artifacts to the WebSockets topic ([KTOR-1532](https://youtrack.jetbrains.com/issue/KTOR-1532))
* Missing dependency information the Client Auth topic ([KTOR-1533](https://youtrack.jetbrains.com/issue/KTOR-1533))
* New documentation lacks artifacts information for Gradle and Maven ([KTOR-1167](https://youtrack.jetbrains.com/issue/KTOR-1167))
* ResponseException is no longer serializable starting from 1.4.0 (breaking change) ([KTOR-1552](https://youtrack.jetbrains.com/issue/KTOR-1552))
* Upgrade kotlin to 1.4.21 ([KTOR-1637](https://youtrack.jetbrains.com/issue/KTOR-1637))

# 1.5.0
> Published 22 Dec 2020

* Fixed crash when sending large responses in 1.4.2 ([KTOR-1369](https://youtrack.jetbrains.com/issue/KTOR-1369))
* Introduced URLBuilder function to append paths ([KTOR-403](https://youtrack.jetbrains.com/issue/KTOR-403))
* Allowed `OkHttpConfig` to configure `WebSocket.Factory` ([KTOR-951](https://youtrack.jetbrains.com/issue/KTOR-951))
* Get client certificate information from request ([KTOR-424](https://youtrack.jetbrains.com/issue/KTOR-424))
* Fixed quoting `Content-Disposition` additional parameters ([KTOR-455](https://youtrack.jetbrains.com/issue/KTOR-455))
* Support Java HTTP Client ([KTOR-348](https://youtrack.jetbrains.com/issue/KTOR-348))
* Serializing collections of different element types ([KTOR-1163](https://youtrack.jetbrains.com/issue/KTOR-1163))
* Introduced Netty `tcpKeepAlive` option ([KTOR-368](https://youtrack.jetbrains.com/issue/KTOR-368))
* Implemented development mode for Ktor ([KTOR-1184](https://youtrack.jetbrains.com/issue/KTOR-1184))
* Implemented proper unhandled exception handling strategy ([KTOR-835](https://youtrack.jetbrains.com/issue/KTOR-835))
* Added OAuth feature config to avoid Dropbox issue ([KTOR-715](https://youtrack.jetbrains.com/issue/KTOR-715))
* **Breaking change**: Fixed trailing slashes handling in routing ([KTOR-372](https://youtrack.jetbrains.com/issue/KTOR-372))
  Routes registered without trailing slashes no longer match URLs with trailing slashes, and vice versa. To keep the previous behavior, install the `IgnoreTrailingSlash` feature.
* Added CIO client proxy tunneling support ([KTOR-1458](https://youtrack.jetbrains.com/issue/KTOR-1458))
* Supported Sealed Classes inside Session-Objects ([KTOR-826](https://youtrack.jetbrains.com/issue/KTOR-826))
* Fixed code autoreload ([KTOR-664](https://youtrack.jetbrains.com/issue/KTOR-664))
* Added response text to the message of `ResponseException` and derived
  exceptions ([KTOR-844](https://youtrack.jetbrains.com/issue/KTOR-844))
* Added ability to send cookies with `HttpRequestBuilder` ([KTOR-926](https://youtrack.jetbrains.com/issue/KTOR-926))
* Added warning to HTTP/2 push API ([KTOR-1329](https://youtrack.jetbrains.com/issue/KTOR-1329))
* Fixed parsing Authorization header diagnostics ([KTOR-1406](https://youtrack.jetbrains.com/issue/KTOR-1406))
* Fixed CORS character encoding issue ([KTOR-1370](https://youtrack.jetbrains.com/issue/KTOR-1370))
* Added CORS `anyHeader` in feature configuration ([KTOR-977](https://youtrack.jetbrains.com/issue/KTOR-977),
  [KTOR-1263](https://youtrack.jetbrains.com/issue/KTOR-1263))
* Added curl engine option sslVerify ([KTOR-1093](https://youtrack.jetbrains.com/issue/KTOR-1093))
* Fixed client response validation in some cases ([KTOR-1412](https://youtrack.jetbrains.com/issue/KTOR-1412))
* Introduced support for pre-compresed files ([KTOR-1447](https://youtrack.jetbrains.com/issue/KTOR-1447))
* Fixed Apache client engine sometimes hits an unrecoverable socket timeout when using
  ChannelWriterContent ([KTOR-1149](https://youtrack.jetbrains.com/issue/KTOR-1149))
* Fixed typo `val socketTimeout` in `CIOEngineConfig` cause it's a property in the
  config ([KTOR-1240](https://youtrack.jetbrains.com/issue/KTOR-1240))
* Added excludeSuffix to HttpsRedirect feature ([KTOR-1197](https://youtrack.jetbrains.com/issue/KTOR-1197))
* Fixed CIO client connectRetryAttempts = 0 handling ([KTOR-1125](https://youtrack.jetbrains.com/issue/KTOR-1125))
* Added option to use specific alias from keystore in CIO TLSConfigBuilder JVM
  ([KTOR-941](https://youtrack.jetbrains.com/issue/KTOR-941))

# 1.4.3
> Published 1 Dec 2020

* Client: URL encode / escaping is wrong ([KTOR-341](https://youtrack.jetbrains.com/issue/KTOR-341))
* HTTP/2 push fails with netty engine ([KTOR-800](https://youtrack.jetbrains.com/issue/KTOR-800))
* Request headers exceeding expected threshold are not handled correctly ([KTOR-905](https://youtrack.jetbrains.com/issue/KTOR-905))
* iOS client fails with CoroutinesInternalError when Logging is used ([KTOR-924](https://youtrack.jetbrains.com/issue/KTOR-924))
* Experimental API and compatibility guarantees ([KTOR-1035](https://youtrack.jetbrains.com/issue/KTOR-1035))
* CIO: client engine exceptions are both logged and thrown ([KTOR-1127](https://youtrack.jetbrains.com/issue/KTOR-1127))
* Timeout﻿ feature: android engine throws Java's SocketTimeoutException instead of ConnectTimeoutException ([KTOR-1229](https://youtrack.jetbrains.com/issue/KTOR-1229))
* Input.readTextExactBytes(n) on empty input different behavior per platform ([KTOR-1235](https://youtrack.jetbrains.com/issue/KTOR-1235))
* HttpRedirect feature alters Location header value ([KTOR-1236](https://youtrack.jetbrains.com/issue/KTOR-1236))
* Wrong pool is used to release `IOBuffer` after `ByteChannelSequential.copyTo` from static initialized instance. ([KTOR-1237](https://youtrack.jetbrains.com/issue/KTOR-1237))
* CIO Engine's HttpClient may fail when trying to send large size binary data. ([KTOR-1247](https://youtrack.jetbrains.com/issue/KTOR-1247))
* `ByteBufferChannel.readRemaining` doesn't read whole channel ([KTOR-1268](https://youtrack.jetbrains.com/issue/KTOR-1268))
* Cannot receive content via jackson negotiator since 1.4.2 ([KTOR-1286](https://youtrack.jetbrains.com/issue/KTOR-1286))
* ktor-io: JVM shared function decrease performance starting from 1.4.0 ([KTOR-1290](https://youtrack.jetbrains.com/issue/KTOR-1290))
* Sessions + SSL (Netty) ([KTOR-1292](https://youtrack.jetbrains.com/issue/KTOR-1292))
* Netty HTTP/2 HEAD response hangs ([KTOR-1298](https://youtrack.jetbrains.com/issue/KTOR-1298))
* Using blocking primitives on this dispatcher is not allowed. Consider using async channel instead or use blocking primitives in withContext(Dispatchers.IO) instead. ([KTOR-1305](https://youtrack.jetbrains.com/issue/KTOR-1305))
* "Wrong HEX escape": gracefully handle invalid URLs ([KTOR-1308](https://youtrack.jetbrains.com/issue/KTOR-1308))
* Add build parameter to build ktor with JVM IR compiler ([KTOR-1336](https://youtrack.jetbrains.com/issue/KTOR-1336))
* Update kotlin to 1.4.20 ([KTOR-1346](https://youtrack.jetbrains.com/issue/KTOR-1346))
* Fix configuration if project without VPN and cache ([KTOR-1347](https://youtrack.jetbrains.com/issue/KTOR-1347))
* Client: NPE in FormDataContentKt -> Input.copyTo ([KTOR-1349](https://youtrack.jetbrains.com/issue/KTOR-1349))
* Upgrade Netty to 4.1.54.Final ([KTOR-1363](https://youtrack.jetbrains.com/issue/KTOR-1363))
* Handle failure in reading request body ([KTOR-1367](https://youtrack.jetbrains.com/issue/KTOR-1367))
* Remove copyTo usage from ServerPipeline ([KTOR-1381](https://youtrack.jetbrains.com/issue/KTOR-1381))

# 1.4.2

> Published 10 Nov 2020

Please see [Change Log on Ktor site](https://ktor.io/changelog/#version-1-4-2)

# 1.4.1
> Published 23 Sep 2020

* OkHttp: Can't reuse same HttpRequestBuilder for different network clients ([KTOR-949](https://youtrack.jetbrains.com/issue/KTOR-949))
* Empty body in response using macosX64 target ([KTOR-479](https://youtrack.jetbrains.com/issue/KTOR-479))
* Native: InvalidMutabilityException creating HttpClient ([KTOR-915](https://youtrack.jetbrains.com/issue/KTOR-915))
* MultiPartData.readAllParts() throws java.io.IOException when multipart list is empty ([KTOR-767](https://youtrack.jetbrains.com/issue/KTOR-767))
* kotlin.native.concurrent.InvalidMutabilityException: mutation attempt of frozen io(.ktor.client.request.HttpRequestPipeline ([KTOR-693](https://youtrack.jetbrains.com/issue/KTOR-693))
* "FreezingException: freezing of InvokeOnCompletion has failed" using native-mt coroutines ([KTOR-973](https://youtrack.jetbrains.com/issue/KTOR-973))
* kotlin.native.concurrent.InvalidMutabilityException with 1.3.3-native-mt ([KTOR-497](https://youtrack.jetbrains.com/issue/KTOR-497))
* Parser Exception in header with character code 1 not allowed ([KTOR-860](https://youtrack.jetbrains.com/issue/KTOR-860))
* Calling HttpStatement#toString more than once throws IllegalArgumentException ([KTOR-1005](https://youtrack.jetbrains.com/issue/KTOR-1005))
* Wrong session id get stuck at clients ([KTOR-1007](https://youtrack.jetbrains.com/issue/KTOR-1007))
* Exception after WebSocketSession.close() invocation. ([KTOR-847](https://youtrack.jetbrains.com/issue/KTOR-847))
* Error Ktor running on background thread on iOS ([KTOR-499](https://youtrack.jetbrains.com/issue/KTOR-499))
* HttpClient can only be used on the main thread for native targets ([KTOR-491](https://youtrack.jetbrains.com/issue/KTOR-491))
* Ignore content length when transfer encoding is chunked for CIO server ([KTOR-1036](https://youtrack.jetbrains.com/issue/KTOR-1036))
* ConcurrentList.increaseCapacity() throws ArrayIndexOutOfBoundsException ([KTOR-1034](https://youtrack.jetbrains.com/issue/KTOR-1034))
* Ktor 1.3.1 Fails File Upload with MalformedInputException ([KTOR-391](https://youtrack.jetbrains.com/issue/KTOR-391))
* Update library versions, fix config after release ([KTOR-1027](https://youtrack.jetbrains.com/issue/KTOR-1027))
* Fix parsing urls with trailing spaces ([KTOR-886](https://youtrack.jetbrains.com/issue/KTOR-886))
* 1.4.0: breaking change by making response nullable in ResponseException ([KTOR-916](https://youtrack.jetbrains.com/issue/KTOR-916))
* Netty: Not started servers leak resources ([KTOR-939](https://youtrack.jetbrains.com/issue/KTOR-939))
* Ktor websocket client passes configured max frame as timeout millis ([KTOR-923](https://youtrack.jetbrains.com/issue/KTOR-923))
* Routing: get matcher has higher priority than param matcher on the same level ([KTOR-792](https://youtrack.jetbrains.com/issue/KTOR-792))
* Confusing log message about failed session lookup ([KTOR-776](https://youtrack.jetbrains.com/issue/KTOR-776))
* Implement runtime check of using `native-mt` coroutines ([KTOR-956](https://youtrack.jetbrains.com/issue/KTOR-956))
* Http parse security issue ([KTOR-841](https://youtrack.jetbrains.com/issue/KTOR-841))
* Bumped versions:
    - kotlinx.coroutines 1.3.9-native-mt-2
    - kotlinx.serialization 1.0.0-RC2
    - kotlin 1.4.10

# 1.4.0
> Published 18 Aug 2020

* Upgrade to kotlin 1.4.0
* Add native platform support for CIO client (#2021)
* Prevent access Tomcat servletRequest after recycling
* Fix verbose IO exception logging
* Fix client cookies remove
* Fix suspend tests for digest provider
* Add deprecation to BasicAuth feature
* Add client.get operator for features
* Add client websocket feature config
* iOS Certificate Pinning (#1750)
* Add originHost support in browser
* Fix client logging issues with POST body
* Prevent CURL multi-handle double close
* Add content-type header to default transformers
* Fix report for multiple failed engines in native
* Use window.location.origin as default host in URLBuilder
* Prevent Empty Cookie addition (#2008)
* Fix executor service termination in okhttp (#1860)
* Verify sending Content-Type and custom object body via POST (#1897)
* Fix ByteBufferPool recycle (#2016)
* Update jetty version
* Fix CIO exception logged twice
* Change exception type for long strings in readUtf8Line
* Fix uri field in digest auth header to include query params (#1992)
* Fix empty multipart post
* Move the default test server to CIO
* Fix webpack warning about ktor-client-core critical dependency
* Fix missing qop in DigestAuthProvider (Issue #1974)
* Handle " in different position cases
* Fix parsing of quoted header parameter value
* Fix saved call early completion
* Fix tests with empty json check
* Fix sending blank ContentType in Apache engine
* Parse blank content type to Any
* Fixed serialization of empty body (#1952)
* JsonFeature: Fixed header behavior and made it more flexible (#1927)
* Fix max-age header to use '=' instead f ':'. (#1769)
* Add contextual serialization support
* Introduce non-suspend api for writing
* fix memory alignment check (#1742)
* JetBrains Toolbox icon (#1805)
* Apache should use existing approach when merging headers (#1919)
* Fix conditional headers behaviour (Fix #1849).
* Change IosHttpRequestException parent to improve usability
* OAuth2: Added option to pass params in URL (#1847)
* Fix doubling host
* Enhanced handling of statusCode for AndroidEngine (#1852)
* Fix deserialization issue in client (Fix #1800).
* GitHub issue/pr links in IDEA Git log (#1806)
* Fix log channel is not closed for ByteArrayContent (#1808)
* Use comma to divide headers (Fix #1765).
* HTTP Client tracing using Stetho Android library.
* Fix static content resolution for directories inside Jar (#1777).
* Improve WebSocket routing API (Fix #1075).
* Implemented cookies encoding with their own encoding and added test cases for this
* Fix URI support (#1755)
* Fix OkHttp WebSocket close reason completion (#1363).
* Fix package directive; Add unimported cfnetwork constants
* Fix client.join to wait engine close
* Verify receive from response in exception
* Update okhttp version to 4.4.0
* Cleanup iOS proxy configuration
* Fix WebSocketTest.testMaxSize
* Fix UTF8 parsing (#1718).

# 1.3.2
> Published 12 Mar 2020

* Introduced iOS streaming response support
* Provided challenge handler in iOS client configuration
* Improved JsonFeature configuration DSL (#1472)
* Simplified server kotlinx.serialization config
* Recovered `HttpRequestBuilder.takeFrom` function (#1626)
* Allowed to configure default cookies asynchronously
* Exposed server `LocationInfo` and added location resolve functions (#1657)
* Introduced function for async writing to server response channel (#1703)
* Added an option to filter logged calls for ktor-client-logging
* Fixed iOS client timeouts
* Fixed iOS crash
* Fixed 100% CPU Apache Ktor Http Client #1018 (#1689)
* Fixed missing client response logging
* Fixed CIO server local address detection (#1663)
* Fix server request origin to provide header's host and port when available
* Fixed random missing feature errors caused by concurrency at startup (#1694)
* Fixed `Set-Cookie` header parser in iOS and JS
* Fixed client multiple redirects with relative path (#1704)
* Fixed unwrapping cancellation exceptions in client (#1482)
* Fixed missed preconfigured `OkHttpClient` in `OkHttpEngine` (#1646)
* Fixed websocket to complete closeReason on disconnection (#1275).
* Fixed websocket sending CloseReason(1009) when frame is too big
* Fixed websocket pinger logging
* Fixed maxFrameSize for chunked frames
* Fixed URL scheme parser (#1614)
* Fixed platform detection in `PlatformUtils.IS_NODE` and `PlatformUtils.IS_BROWSER` (#1675)
* Fixed `Short.highByte`
* Fixed consumeEachBufferRange (#1693)
* Fixed httpclient gzip decoding failure (#1189)
* Fixed `InputStream` wrapper for `Input`
* Bumped versions:
    - Kotlin 1.3.70
    - kotlinx.coroutines 1.3.4
    - kotlinx.serialization 0.20.0
    - kotlinx.html 0.7.1
    - dropwizard 4.1.2
    - slf4j 1.7.30
    - mustache 0.9.6
    - pebble 3.1.2
    - webjars 0.43
    - jackson 2.10.2

# 1.3.1
> Published 5 Feb 2020

* Introduced experimental client timeout feature
* Fixed OkHttp leaking threads
* Fix decoding UTF8 lines (#1323)
* Fixed websocket close sequence (#1262, #1571, #1427)
* Reduced number of redundant exceptions logged in jetty server
* Fixed text decoder in js client under nodejs
* Fixed NSUrlSession memory leak (#1420)
* Make server feature throw `BadContentTypeFormatException` to get status 400
* Reverted accidentally removed client `LocalFileContent`
* Removed User-Agent header in browser (to avoid CORS-related issues)
* Fixed request body with `Input`
* Improved native clients performance
* Fixed native and js client cancellation issues
* Fixed file descriptor leak in Jetty server (#1589)
* Fixed server sessions and cache related iseues (#1591)
* Upgraded JWT/JWKS versions
* Upgraded Netty version
* Fixed multiple server jwt auth providers processing (#1586)
* Fixed Auth retry logic (#1051)
* Fixed ApplicationRequest.remoteHost to not report "unknown"
* Fixed corrupted headers in CIO client and server on Android
* Improved server cancellation handling

# 1.3.0
> 14 Jan 2020

* ktor client proxy support
* Introduced `HttpStatement` and deprecated potentially dangerous resource-leaking client API
* Eliminated kotlinx.io dependency
* Fixed server identity compression handling: keep original content length
* Fixed handling GET requests with body (#1302)
* Fixed curl request with empty body
* Added iOS url session configuration
* Fixed CIO engine no longer sends port in "Host" header (#1295)
* Add INTRINSIC value to TLS signature algorithms
* Introduced ability to send string in request body
* Improved client and server typeOf support with kotlinx.serialization
* Gradle 5.4.1+ with newer metadata (metadata 1.0)
* Improved exceptions handling in client and server on Android
* Added missing TLS parameters and relaxed TLS parsing to ignore unsupported features
* Improved session diagnostics (#1368)
* Fixed `hookRequests` in test engine (#1300)
* Deprecate java.time related API and related cleanup (for future kotlin.time support)
* Restricted CIO HTTP headers parser
* Introduced header name and value validation
* Fixed must-revalidate on the request side in ktor client (#1406)
* Fixed OkHttp client resource cleanup on close
* Added watchos/tvos native targets
* Fixed content truncation at native and JS targets
* Fixed server's `If-Range` header parsing to avoid crash at date parsing (#1377)
* Fixed server's conditional headers processing
* Reduced required JDK version for `DefaultHeaders` server feature
* Fixed client hanging due to exception in response pipeline
* Replaced HttpClientJvmEngine to HttpClientEngineBase that is now common for all platforms (affects only custom client engines)
* Fixed hierarchy of execution and call contexts in clients that allows to properly handle request lifetime using execution context.
* Optimize JS module import time (#1464)
* Upgraded versions of Netty, Jetty and Tomcat implementations
* Added Pebble template engine (#1374)
* Introduced localPort route that is always tied to actual socket port (#1392)
* Fixed cookie expiration date parsing (#1390)
* Server authentication feature's phases are now public (#1160)
* Fixed auth header resending after redirect (#1467)
* TCP half-close made optional for CIO client engine and disabled by default.
* Apache client random timeouts fixed
* Fixed locale-dependant code (#1491)
* Fixed unclosed websocket channels if cancelled too early
* TCP half-close made optional for CIO client engine and disabled by default. (#1456)
* Improved ktor-client-mock engine to be thread safe (#1505)
* Fixed client cookies logging (#1506)
* Fixed multiple application stop events in test engine (#1498)
* Fixed CIO ActorSelectorManager to not spin due to cancelled keys (affects both CIO client and server)
* Made default auth validate functions fail to force users to implement them
* Introduced test client instance in the test server
* Fixed various server and client engines to return `null` for missing headers rather than empty list
* Introduced support for json structures in client and server (#1519)
* Introduced ktor-server-core binary compatibility tracking
* kotlinx.coroutines 1.3.3

# 1.2.6
> 25 Nov 2019

* Kotlin 1.3.60
* Restricted CIO HTTP headers parser
* Introduced header name and value validation

# 1.2.5
> 27 Sep 2019

* Fixed `ClosedSendChannel` exceptions in client and server
* Fixed Android crash on client and server exceptions
* Fixed server identity compression handling: keep original content length
* Fixed partial content without the passed range doesn't have content length
* Fixed curl request with empty body
* Fixed empty client form data (#1297)
* Fixed CIO engine no longer sends port in "Host" header (#1295)
* Fixed potential deadlock in Android engine
* Gradle Metadata 1.0 (Gradle 5.6.2)
* kotlinx.coroutines 1.3.2
* kotlinx.serialization 0.13.0
* Dropwizard 4.1.0
* jackson 2.9.9.3

# 1.2.4
> Published 2 Sep 2019

* Fixed multipart form header entity separator
* Fixed crypto in IE11 (#1283)
* Marked response transient in the client exception (#1256)
* Fixed network on main thread in okhttp engine close
* Fixed follow redirect iOS (#1000)
* Kotlin 1.3.50
* kotlinx.coroutines 1.3.0

# 1.2.3
> Published 1 Aug 2019

* JS websocket bugs fixes and improvements
* Eliminated Java9's Base64 implementation (useful for older JDK and Android)
* Fixed bug of adding unexpected trailing slash (#1201) (#1206)
* Improved apache and okhttp client engines performance
* Fixed client response body cancellation
* Added client response streaming on nodejs
* Deprecated old client `BasicAuth`
* Introduced a flag to send auth without negotiation
* Added server kotlinx.serialization initial support (`SerializationConverter`)
* Client TLS implementation fixes: cancellation and error handling.
* Added web assembly content type.
* Prohibited server double request content `call.receive`.
    * Introduce `DoubleReceive` feature that makes it work.
* Server CORS support fixed and improved
* Added initial kotlinx.serialization support for server including receiving generic collections.
* Introduced `ktor-bom` for better dependency management.
* Improved jetty server engine configuration to enable manual connectors setup.
* Fixed client memory leak (#1223).
* Upgraded Jetty, Netty and utility libraries.
* Kotlin 1.3.41


Breaking changes/Migration steps:
* CORS doesn't allow non-simple request body content types anymore by default
  * to allow extra content types such as json, enable `allowNonSimpleContentTypes`
* At least Kotlin 1.3.41 IS REQUIRED

# 1.2.2
> Published 20 June 2019

* Upgraded to Kotlin 1.3.40.
* Netty server engine uses native transports when available (#1122).
* Upgraded to Netty 4.1.36 (#1190).
* Added JVM shutdown hooks in server engines (#1111, #1164).
* Introduced challenge builder functions in server auth providers (#366, #921, #1130, #798).
* Segmentation fault is fixed in native clients (#1135).
* Improved gracefull shutdown in ktor client engines Jetty and Apache.
* Removed kotlin-reflect from ktor jvm clients (#1162).
* Client threads daemonized (#1170).
* Relaxed client cookie value restrictions (#1069).
* Fixed empty client requests with okhttp engine (#1175).
* API cleanup, deprecations.
* kotlinx.coroutines 1.2.2, kotlinx.serialization 0.11.1.

# 1.2.1
>  Published 27 May 2019

* Fixed module function lookup (regress, #1132)
* Fixed SessionTransportTransformers application order (#1147)
* Fixed double content length header in requests on older Androids (#1060)
* Fixed receiving a byte channel crash on Android (#1140)
* Fixed websocket sockets lifecyle on Jetty engine
* Downgraded Gradle to reduce gradle metadata version

# 1.2.0
> 14 May 2019

* Introduced multiplatform websockets: jvm, js.
* Added client certificates support.
* Fixed updating session in directory storage (#963).
* Added optional contentType to formDsl (#910).
* `MockEngine` version which favors execution order and returns processed requests (#924).
* Fixed `Unit` body serialization.
* Allowed using preconfigured OkHttp client instance.
* Defined the client default user agent.
* Improved curl error diagnostics and resource management.
* Fixed LogLevel.NONE with body bytes.
* Added CIO endpoint config builder.
* Fixed status code check in client Auth feature.
* Fixed client close issue.
* Fixed: `GMTDate.toJvmDate()` uses current date instead of given date. (#986)
* Moved client auth to common.
* Use `UTF-8` as default charset in BasicAuth.
* Introduced client content encoding feature.
* Introduced client call validator feature.
* Old API deprecations and removals.
* Fixed gzip/deflate on JDK11
* Introduced JWT auth header retrieval configuration (#1048)
* `CallLogging` message format customization (#1047)
* Fixed logging error with no call-related MDC (#1033)
* Avoided using constant hash salt in `UserHashedTableAuth`
* Added LDAP auth provider proper characters escaping
* Minimized jetty core pool size and made it configurable (#1083)
* Made servlet engine use servlet config instead of context (#1063)
* Introduced accepted content types contributor in ContentNegotiation (#357)
* Introduced `ApplicationEnvironment.rootPath` with servlet engine support (context path) (#738)
* Support for `rootPath` in routing by default (#738)
* Introduce ability to serve web resources from WAR
* Added micrometer metrics (#1037)
* Added Thymeleaf templating feature (#988)
* Cookie session use `/` path by default (#1043)
* Add hot reload experimental support for JDK9+ (VM option required)
* `HttpStatusCode` equals check is amended
* Added client `AcceptCharset` header support
* `KotlinxSerializer` moved to a separate artifact
* Client engine API simplified
* Introduced client cache support
* Server authenticator config reworked
* Server digest auth updated to use UTF-8
* Added experimental android client line-wrapping logger
* Fixed webjars parameters handling and several minor fixes
* Introduced JWTVerifier configure block when using JWKProvider
* Added client json feature custom content types support
* Fixed incorrect url encoding for some characters (#1094)
* Fixed hanging jetty server engine
* Introduced CIO client engine request timeout config
* Added client multipart content length support
* Jetty upgraded to 9.4.15.v20190215
* okhttp client upgraded to 3.14.0
* Fixed CIO client CPU utilization issue
* Kotlin 1.3.31
* kotlinx.coroutines 1.2.1

# 1.1.5
> Published 24 Apr 2019

* Minimized jetty core pool size and make it configurable (#1083)
* Servlet engine fixed to use servlet config instead of context (#1063)

# 1.1.4
> Published 13 Apr 2019

* Upgrade to Kotlin 1.3.30
* Upgrade coroutines to 1.2.0

# 1.1.3
> Published 21 Feb 2019

* Fixed NoSuchMethodError on Android (#927)
* Fixed upload file error on JS (#956)
* Fixed several encodings issues caused corrupted jsons and receive pipeline erros (#920, #787, #866)
* Fixed curl connection errors reporting
* Updated jackson dependency (#952)

# 1.1.2
> Published 24 Jan 2019

* Introduced native curl client engine (#479)
* Added iosArm32 target (except curl)
* Host and port route builders (#825)
* Fixed `host()` and `port()` functions to respect proxy (#834)
* Fixed classloading issue affecting hot-reload (#825)
* Fixed hanging CIO client (#800, #806)
* Added CIO client CBC support (#623, #560, #394)
* Upgraded JWKS/JWT (#856)
* Fixed server `MessageDigest` concurrent issues
* Introduced `NonceManager`, deprecated `OAuth2StateProvider`
* Prohibited setting session at server after responding the call (#864)
* Fixed loosing errors in `StatusPages` if there was already a response sent
* Introduced `application` property on `ApplicationEngine` interface
* Introduced experimental ktor server exceptions
  * `BadRequestException`
  * `NotFoundException`
  * `MissingRequestParameterException`
  * `ParameterConversionException`
  * supported in locations out of the box (including #767)
  * experimental parameters delegation support
* Added routing tailcard prefix support (#876, #526)
* Fixed registering content converters with custom content type pattern (#872)
* Improved GSON error diagnostics (#811)
* Exclude several content types in Compression feature by default: audio, video, event-stream (#817)
* Fixed hanging handleWebSocketConversation
* Fixed cookie session max-age value to be bumped to Int.MAX_VALUE (#892)
* Fixed CIO headers parsing: allowed headers with no values
* Fixed client websocket nonce size (#861)
* Fixed client scheme parsing
* Supported client relative redirects by making #takeFrom() resolve relative urls. (#849)
* Fixed network on main thread in CIO (#764)
* Changed the default algorithm to SHA-256 from SHA-1 for signed server cookies
* Fixed conflicting `WebSockets` and `StatusPages` (#889)
* Update gradle to 4.10
* Kotlin 1.3.20, kotlinx.coroutines 1.1.1, kotlinx.serialization 0.10.0

# 1.1.1
> Published 26 Dec 2018

* Fixed broken pom files

# 1.1.0
> Published 24 Dec 2018

* Reduced JDK7/8 dependencies (including #762)
* Discarded deprecated API
* Coroutines debug agent support
  * see Kotlin/kotlinx.coroutines/core/kotlinx-coroutines-debug/README.md
* Fixed `IndexOutOfBounds` during main module function search
* okhttp-client: pass `contentLength` for multipart form data
* Improved auto-reloading feature in corner-cases (#783)
* Fixed HTTP server upgrade to delay socket close (#674)
* Added Mustache templating feature (#713)
* Added ability to configure `Logger` instance for `CallLogging`
* Fixed session HMAC to do a constant time comparison
* Added Node.js suport for js http client (#791)
* `SessionTransportTransformerDigest` is deprecated (#792)
  * also switched to SHA-384 by default that is not vulnerable
* Fixed jetty server thread names (#756)
* Fix conditional headers zoned dates (#476)
* `NettyChannelInitializer` made public (#286)
* Fixed slf4j dependency (#808)
* Fixed client logging freeze
* Added client `DigestAuth` feature
* Fixed apache client response reading freeze
* Fixed apache client engine errors handling
* Kotlin 1.3.11, kotlinx.coroutines 1.1.0

# 1.0.1
> Published 4 Dec 2018

* Client logging feature (`Logging` in `ktor-client-logging`)
* Client user agent feature (see `UserAgent`)
* Client top-level list serialization support (#739)
* JS client body handling fix (#746)
* Client redirect edge cases fixed
* Client close/cancel handling improved
* CIO client bugfixes
* Unicode filenames support in multipart fileupload (#757, #687, #596)
* `ContentNegotiation` and `WebSocket` server features compatibility (#745)
* Server session cookie duration is optional (#736)
* Server autreload support fixed (#736)
* `CachingOptions` resolution ambiguity fixed (#741)

# 1.0.0
> Published 19 Nov 2018

* Improved documentation
* Improved performance
* Kotlin 1.3.10
* kotlinx.coroutines 1.0.1 and structured concurrency support:
    * Coroutine scopes introduced per pipeline, call, application and web socket session
* Fixed client response cancelation via `receive<Unit>()` and `response.cancel()`
* Disabled client pipelining by default to provide safe behavior by default
* `GMTDate` improvements on js and native
* Added client response observer for response logging purpose
* Test client and mock engine improvements
* Cookies dates, domains and dupicate parameters processing fixed
* Server pipeline fixed to discard request body if not used (#609, #574)
* Websocket session lifecycle fixed during close sequence
* Several `Url` and `UrlBuilder` fixes and improvements
* Introduced `ExpectSuccess` client feature
* Fixed `StatusPages` to handle child job failures (#646)
* Compression bugfixes (including #708, #685)
* Fixed timeouts in websockets with Jetty
* Renamed `DevelopmentEngine` to `EngineMain`
* Restricted `@Location` annotation targets, allow on a typealias (#539)
* Removed default connector on port 80 (#670)
* Several JWT and OAuth bugfixes and error habdling improvements (#664, #676,  #550)
* Improved serialization client feature


# 0.9.5
> Published 19 Sept 2018

* Added shorthand client functions for HEAD, OPTIONS, PATCH and DELETE methods (#562)
* URLBuilder's parser improved (#553, #567)
* Improved client's cookie matching and processing
* Introduced CallId feature
* Added MDC support to CallLogging feature
* Fixed setting charset encoding for non-text content types
* Added `respondOutputStream { }` response function
* Migrated to Kotlin 1.2.70
* Split Infrastructure phase into Monitoring and Features phases

# 0.9.4
> Published 29 Aug 2018

* Added multiplatform client support (android and ios)
* Added `Android` client engine (`UrlConnection`)
* Added `OkHttp` client engine (Android support)
* Added `Jackson` feature support
* Added `Ios` client engine
* Deprecated `response.contentType` and `response.contentLength`
* Strengthened `IncomingContent` deprecation
* Upgraded Jetty ALPN, Tomcat
* Fixed config evaluation issues (#488)
* Disabled async incoming upgrade stream at Tomcat
* Prohibited appending unsafe headers to request headers
* Renamed `XForwardedHeadersSupport` to `XForwardedHeaderSupport` (#547)
* Added `HttpResponse.receive<T>` method to run response pipeline on raw response
* Introduced kotlin multiplatform url-parser
* Supported client form data and multipart
* Added missing client builders for `post` and `put` methods
* Simplify client configuration API
* Fixed several compression issues
* Fixed client attributes evaluation
* Fixed CIO engine random algorithm selection
* Fixed url parsing (#511)
* Fixed ambiguity in writing client `Content-Type` and `Content-Length` headers
* Minor performance fixes
* Netty HTTP/2 fixes
* Fixed IOOBE in satic resource resolution (#493)
* Fixed JWT error handling
* Kotlin 1.2.61, kotlinx.coroutines 0.25.0

# 0.9.3
> Published 26 Jun 2018

* Improved WebSocket API
* Websocket header `Sec-WebSocket-Key` is now optional
* Fixed client cookies rendering to avoid `x-enc`
* Fixed plain text client reader (#392)
* Added EC support in CIO TLS (#394: ECDHE_RSA_AES256_SHA384, ECDHE_RSA_AES128_SHA256)
* Fix client certificate validation
* Introduced optional authentication
* Added `ApplicationCall` as receiver for auth `validate` functions
* Introduced `call.respondBytes` (#395)
* Improved JWT support: multiple schemes, nullable issuer
* Conversion service enum type diagnostics improved (#403)
* Avoided using apos entity in HTML escaping as IE doesn't support it (#400)
* Converter support for java big numbers
* Ability to add auth methods to existing feature on the fly
* Improved auth header scheme and contents validation (#415)
* Default charset for BasicAuth is now UTF-8 (#420)
* Added `ByteArrayContent.contentLength` (#421)
* Fixed `headersOf` case insensitive issue (#426)
* Client deserialization improved by using type token
* Ability to disable client default transformers
* Explicit `Accept` header in client request
* Turn on masking in client websockets (#423)
* Fixed inverted `PartialContent.Configuration.maxRangeCount` check (#440)
* Fixed uncaught `UnsupportedMediaTypeException` from `receiveOrNull()` (#442)
* Fix multipart boundary header parsing
* Upgraded jwks/jwt, applied RSA256 by default if unspecified (#434, #435)
* Upgrade coroutine version to 0.23.3
* Upgrade Jetty version to 9.4.11.v20180605
* Add `client-mock-engine` for testing purpose
* Use default available engine by deafult
* Upgrade kotlin to 1.2.50

Move ktor-samples to a separate repository (#340). https://github.com/ktorio/ktor-samples

# 0.9.2
> Published 23 Apr 2018

* New auth DSL, more suspendable functions (such as `verify`/`validate`)
* `RoutingResolveTrace` for introspecting routing resolution process
* HTTP client improvements and bugfixes (DSL, reconnect, redirect, cookies, websockets and more)
* CIO http client pipelining support, chunked and more
* CIO initial TLS support
* Session authentication provider
* OAuth2: introduce ability to generate and verify state field
* OAuth: fix scopes parameter to conform to RFC (#329)
* OAuth2: fix bug with double scopes encoding (#370)
* OAuth2: add ability to intercept redirect URL
* CORS: introduce `allowSameOrigin` option
* Auth: provide application call as receiver for validate functions (#375 and related)
* Test host reworked, `handleRequest` reads the body and redirects the exceptions correctly
* Servlets: fixed `inputStream` acquisition, fixed error handling
* Java 9 compatibility improved (no modules yet)
* Digest auth fixes (#380)
* Log running connectors details for better development experience (#318)
* `Last-Modified` header and related functionality to work in proper GMT time zone (#344)
* `IncomingContent` is deprecated
* `URLBuilder` fixes and improvements
* Documentation improvements
* Performance optimizations (Netty, CIO server backends)
* CIO server improved stability
* Encrypted session support (`SessionTransportTransformerEncrypt`)
* Empty (`null`) model for freemarker (#291)
* `ContentNegotiation` missing `Accept` header support (#317)

# 0.9.1
> Published 29 Jan 2018

* Support for blocking servlets and GAE
* `Headers` and `Parameters` types instead of `ValuesMap`
* Velocity templates support
* Unsafe (Forbidden) headers checks added
* Deprecated `Resource` type
* Added support for extensible version providers to ConditionalHeaders feature
* Engine-specific configuration support for application.conf
* Added filtering and customisation of log level to CallLogging feature
* Added ability to skip authentication using a predicate, add documentation to Authentication feature
* Introduced auth0 JWT/JWKS authentication (#266)
* ktor http client DSL improvements
* CIO engine improvements, stability and performance
* Introduced [OutgoingContent] properties `contentLength`, `contentType` and `status`
* Upgrade Netty in the corresponding engine implementation
* Introduced `shareWorkGroup` option for Netty engine
* More documentation
* Bump versions of dependencies

# 0.9.0
> Published 31 Oct 2017

* Package structure reworked
* Packages and maven groupId renamed org.jetbrains.ktor -> io.ktor
* Server-related artifacts having ktor-server-* name prefix (ktor-netty -> ktor-server-netty)
* Application Host renamed to Application Engine
* FinalContent renamed to OutgoingContent as opposite to IncomingContent (introduced in 0.4.0)
* Added Application Engine configure facilities so one can specify thread pool size or some engine-specific parameter
* Initial idiomatic ktor HTTP client implementation (artifacts prefixed with ktor-client-*)
* Metrics support, DropWizard integration
* Improve routing API, tune resolution mechanics, hide some implementation details
* ContentNegotiation feature to support variable content on send and receive
* Jackson support
* Experimental pure kotlin application engine on coroutines (CIOApplicationEngine) and CIO-based http client backend
* Improved stability under load
* Status pages processing improvements
* A lot of documentation

# 0.4.0
> Published 16 Aug 2017

* Built with Kotlin 1.1.4
* Refactored receive/response pipelines and moved them into respective ApplicationRequest & ApplicationResponse classes
* Fixes, improvements and integration tests for HTTP/2 support
* Update `ContentType` to treat all parts case insensitively
* Remove `ApplicationLog` and use SLF4J `Logger` directly
* Add HttpMethod.Patch and respective builder functions
* `routing` function will now install `Routing` feature or use existing installed feature for easier modules
* Convert sessions to proper feature, support multiple sessions, improve DSL
* HeadRequestSupport feature is renamed to AutoHeadResponse (with deprecated typealias)
* Replace ApplicationTransform with receive pipeline
* Introduce send/receive pipelines for all call pipelines
* Gson application feature for JSON transformation of incoming & outgoing data objects
* Added HttpBin sample (thanks to @jmfayard)
* Employ `DslMarker` annotation to prevent accidental use of route functions in get/post handlers
* Improve diagnostics for untransformed content
* Ensure missing file (`FileNotFoundException`) can be handled properly with `StatusPages` feature
* Websocket fixes for large frames, fragmentation and more
* Support for specifying config file with command line
* Improvements in Servlet-based hosts
* Memory allocation and performance optimisations
* Add Apache 2 LICENSE file
* Add documentation to some types
* New sample for static content
* Bump versions of dependencies

# 0.3.3
> Published 22 Jun 2017

* Execution model slightly changed to avoid global executors. ApplicationEnvironment doesn't provide `executor` anymore
* Websockets refactored with channels instead of callback functions
* Fixed bug with compression not preserving status code (thanks Diego Rocha)
* Fixes in Netty support: performance issues under heavy load and keep-alive connections, cancellation, closed sockets
* Fixes in session serialization, enums support
* Optimisations in Servlet and Jetty hosts, fixes in edge cases
* Fixes in chat sample

# 0.3.2
> Published 24 Apr 2017

* Fix bug in byte array response that missed Content-Length header
* Fix default encoding in FreeMarker to be UTF-8
* Fix FreeMarker writer
* Fix charset for text/* content types to be UTF-8 by default
* Fix Kweet sample application in kweet deletion
* Fix form authentication to adhere to removal of post parameters from `parameters`
* Rework static content DSL to improve usability
* Improve Accept header handling in routes
* Support local class in locations, improve diagnostics
* Add servlet host tests running in Jetty, but using servlet deployment model
* Benchmarks can now measure GC performance (and any other JMH profiler)
* Performance optimisations


# 0.3.1
> Published 7 Apr 2017

* Replace routing function `contentType` with `accept`
* Major refactoring in internal host system to simplify and unify code
  * Use `embeddedServer(Netty, …)` instead of `embeddedNettyServer(…)`
  * Support automatic reloading in embedded hosts
  * Fix ApplicationTransform problems preventing Freemarker templates from working
* Fix json and logback dependencies to avoid propagating to clients
* Bug fixes & performance optimisations
* Documentation

# 0.3.0
> Published 8 Mar 2017

* Major refactor to coroutines
