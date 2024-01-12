package test.server.tests

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

internal fun Application.webSockets() {
    routing {
        route("websockets") {
            webSocket("echo") {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            send(Frame.Text(text))
                        }
                        is Frame.Binary -> send(Frame.Binary(fin = true, frame.data))
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
                }
            }
            webSocket("headers") {
                val headers = call.request.headers.toMap()
                val headersJson = Json.encodeToString(headers)
                send(Frame.Text(headersJson))
            }
            webSocket("close") {
                for (packet in incoming) {
                    val data = packet.data
                    if (String(data) == "End") {
                        close(CloseReason(1000, "End"))
                    }
                }
            }
            webSocket("echo-query") {
                val param = call.parameters["param"] ?: error("No param provided")
                send(Frame.Text(param))
            }
            webSocketRaw("count-pong") {
                send(Frame.Ping("ping".toByteArray()))

                var countPong = 0
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Pong -> countPong++
                        is Frame.Text -> send(Frame.Text("$countPong"))
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
                }
            }
            webSocket("sub-protocol", protocol = "test-protocol") {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            send(Frame.Text(text))
                        }
                        is Frame.Binary -> send(Frame.Binary(fin = true, frame.data))
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
                }
            }
            get("500") {
                throw IllegalStateException()
            }
        }
    }
}
