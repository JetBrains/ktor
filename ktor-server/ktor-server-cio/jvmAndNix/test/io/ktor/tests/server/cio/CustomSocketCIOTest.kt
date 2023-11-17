package io.ktor.tests.server.cio

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.http.cio.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.cio.*
import io.ktor.server.cio.backend.*
import io.ktor.server.cio.internal.*
import io.ktor.server.engine.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

class CIOSocketClient(private val socket: String) : HttpClientEngineFactory<CIOEngineConfig> {
    override fun create(block: CIOEngineConfig.() -> Unit): CIOSocketClientEngine {
        return CIOSocketClientEngine(socket, CIOEngineConfig().apply(block))
    }
}

@OptIn(InternalAPI::class)
class CIOSocketClientEngine(private val socket: String, config: CIOEngineConfig) :
    CIOHttpClientEngine by CIOEngine(config) {
    override fun createAddress(host: String, port: Int): SocketAddress = UnixSocketAddress(socket)
}

class CIOSocket(private val socket: String) :
    ApplicationEngineFactory<CIOSocketApplicationEngine, CIOApplicationEngine.Configuration> {
    override fun create(
        environment: ApplicationEngineEnvironment,
        configure: CIOApplicationEngine.Configuration.() -> Unit
    ): CIOSocketApplicationEngine = CIOSocketApplicationEngine(socket, environment, configure)
}

class CIOSocketApplicationEngine(
    private val socket: String,
    environment: ApplicationEngineEnvironment,
    configure: CIOApplicationEngine.Configuration.() -> Unit
) : CIOApplicationEngineInterface by CIOApplicationEngine(environment, configure) {

    @InternalAPI
    override fun CoroutineScope.startHttpServer(
        connectorConfig: EngineConnectorConfig,
        connectionIdleTimeoutSeconds: Long,
        handleRequest: suspend ServerRequestScope.(Request) -> Unit
    ): HttpServer {
        val selector = SelectorManager(coroutineContext)
        return httpServer(
            createServer = {
                aSocket(selector).tcp().bind(UnixSocketAddress(socket))
            },
            timeout = WeakTimeoutQueue(connectionIdleTimeoutSeconds * 1000L),
            serverJobName = CoroutineName("server-root-$socket"),
            acceptJobName = CoroutineName("accept-$socket"),
            selector = selector
        ) { request ->
            handleRequest(request)
        }
    }
}
