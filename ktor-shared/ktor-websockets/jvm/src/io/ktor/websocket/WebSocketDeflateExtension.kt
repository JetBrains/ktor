/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.util.*
import io.ktor.websocket.internals.*
import java.util.*
import java.util.zip.*

private const val SERVER_MAX_WINDOW_BITS: String = "server_max_window_bits"
private const val CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover"
private const val SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover"
private const val CLIENT_MAX_WINDOW_BITS = "client_max_window_bits"
private const val PERMESSAGE_DEFLATE = "permessage-deflate"

private const val MAX_WINDOW_BITS: Int = 15
private const val MIN_WINDOW_BITS: Int = 8

/**
 * Compress and decompress WebSocket frames to reduce amount of transferred bytes.
 *
 * Usage
 * ```kotlin
 * install(WebSockets) {
 *     extensions {
 *         install(WebSocketDeflateExtension)
 *     }
 * }
 * ```
 *
 * Implements WebSocket deflate extension from [RFC-7692](https://tools.ietf.org/html/rfc7692).
 * This implementation is using window size = 15 due to limitations of [Deflater] implementation.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.WebSocketDeflateExtension)
 */
public class WebSocketDeflateExtension internal constructor(
    private val config: Config
) : WebSocketExtension<WebSocketDeflateExtension.Config> {
    override val factory: WebSocketExtensionFactory<Config, out WebSocketExtension<Config>> = WebSocketDeflateExtension

    override val protocols: List<WebSocketExtensionHeader> = config.build()

    private val inflater = Inflater(true)
    private val deflater = Deflater(config.compressionLevel, true)

    internal var outgoingNoContextTakeover: Boolean = false
    internal var incomingNoContextTakeover: Boolean = false

    /**
     * Deflater state for incoming frames. Specified if frames should be decompressed until fin packet.
     */
    private var decompressIncoming: Boolean = false

    override fun clientNegotiation(negotiatedProtocols: List<WebSocketExtensionHeader>): Boolean {
        val protocol = negotiatedProtocols.find { it.name == PERMESSAGE_DEFLATE } ?: return false

        incomingNoContextTakeover = config.serverNoContextTakeOver
        outgoingNoContextTakeover = config.clientNoContextTakeOver

        for ((key, value) in protocol.parseParameters()) {
            when (key) {
                SERVER_MAX_WINDOW_BITS -> {
                    // This value is a hint for a client and can be ignored.
                }

                CLIENT_MAX_WINDOW_BITS -> {
                    if (value.isBlank()) continue
                    check(value.toInt() == MAX_WINDOW_BITS) { "Only $MAX_WINDOW_BITS window size is supported." }
                }

                SERVER_NO_CONTEXT_TAKEOVER -> {
                    check(value.isBlank()) {
                        "WebSocket $PERMESSAGE_DEFLATE extension parameter $SERVER_NO_CONTEXT_TAKEOVER shouldn't " +
                            "have a value. Current: $value"
                    }

                    incomingNoContextTakeover = true
                }

                CLIENT_NO_CONTEXT_TAKEOVER -> {
                    check(value.isBlank()) {
                        "WebSocket $PERMESSAGE_DEFLATE extension parameter $CLIENT_NO_CONTEXT_TAKEOVER shouldn't " +
                            "have a value. Current: $value"
                    }

                    outgoingNoContextTakeover = true
                }
            }
        }

        return true
    }

    override fun serverNegotiation(requestedProtocols: List<WebSocketExtensionHeader>): List<WebSocketExtensionHeader> {
        val protocol = requestedProtocols.find { it.name == PERMESSAGE_DEFLATE } ?: return emptyList()
        val parameters = mutableListOf<String>()

        for ((key, value) in protocol.parseParameters()) {
            when (key.lowercase(Locale.getDefault())) {
                SERVER_MAX_WINDOW_BITS -> {
                    check(value.toInt() == MAX_WINDOW_BITS) { "Only $MAX_WINDOW_BITS window size is supported" }
                }

                CLIENT_MAX_WINDOW_BITS -> {
                    // This value is a hint for a server and can be ignored.
                }

                SERVER_NO_CONTEXT_TAKEOVER -> {
                    check(value.isBlank())

                    outgoingNoContextTakeover = true
                    parameters.add(SERVER_NO_CONTEXT_TAKEOVER)
                }

                CLIENT_NO_CONTEXT_TAKEOVER -> {
                    check(value.isBlank())

                    incomingNoContextTakeover = true
                    parameters.add(CLIENT_NO_CONTEXT_TAKEOVER)
                }

                else -> error("Unsupported extension parameter: ($key, $value)")
            }
        }

        return listOf(WebSocketExtensionHeader(PERMESSAGE_DEFLATE, parameters))
    }

    override fun processOutgoingFrame(frame: Frame): Frame {
        if (frame !is Frame.Text && frame !is Frame.Binary) return frame
        if (!config.compressCondition(frame)) return frame

        val deflated = deflater.deflateFully(frame.data)

        if (outgoingNoContextTakeover) {
            deflater.reset()
        }

        return Frame.byType(frame.fin, frame.frameType, deflated, rsv1, frame.rsv2, frame.rsv3)
    }

    override fun processIncomingFrame(frame: Frame): Frame {
        if (!frame.isCompressed() && !decompressIncoming) return frame
        decompressIncoming = true

        val inflated = inflater.inflateFully(frame.data)
        if (incomingNoContextTakeover) {
            inflater.reset()
        }

        if (frame.fin) {
            decompressIncoming = false
        }

        return Frame.byType(frame.fin, frame.frameType, inflated, !rsv1, frame.rsv2, frame.rsv3)
    }

    /**
     * WebSocket deflate extension configuration.
     */
    public class Config {
        /**
         * Specify if the client drops the deflater state (reset the window) after each frame.
         */
        public var clientNoContextTakeOver: Boolean = false

        /**
         * Specify if the server drops the deflater state (reset the window) after each frame.
         */
        public var serverNoContextTakeOver: Boolean = false

        /**
         * Compression level that is used for outgoing frames in the [Deflate] instance.
         */
        public var compressionLevel: Int = Deflater.DEFAULT_COMPRESSION

        internal var manualConfig: (MutableList<WebSocketExtensionHeader>) -> Unit = {}

        internal var compressCondition: (Frame) -> Boolean = { true }

        /**
         * Configure which protocols should send the client.
         */
        public fun configureProtocols(block: (protocols: MutableList<WebSocketExtensionHeader>) -> Unit) {
            val old = manualConfig
            manualConfig = {
                old(it)
                block(it)
            }
        }

        /**
         * Indicates if the outgoing frame should be compressed.
         *
         * Compress the frame only if all conditions passed.
         */
        public fun compressIf(block: (frame: Frame) -> Boolean) {
            val old = compressCondition
            compressCondition = { block(it) && old(it) }
        }

        /**
         * Specify the minimum size of frame for compression.
         */
        public fun compressIfBiggerThan(bytes: Int) {
            compressIf { frame -> frame.data.size > bytes }
        }

        internal fun build(): List<WebSocketExtensionHeader> {
            val result = mutableListOf<WebSocketExtensionHeader>()

            val parameters = mutableListOf<String>()

            if (clientNoContextTakeOver) {
                parameters += CLIENT_NO_CONTEXT_TAKEOVER
            }

            if (serverNoContextTakeOver) {
                parameters += SERVER_NO_CONTEXT_TAKEOVER
            }

            result += WebSocketExtensionHeader(PERMESSAGE_DEFLATE, parameters)
            manualConfig(result)
            return result
        }
    }

    public companion object : WebSocketExtensionFactory<Config, WebSocketDeflateExtension> {
        override val key: AttributeKey<WebSocketDeflateExtension> = AttributeKey("WebsocketDeflateExtension")
        override val rsv1: Boolean = true
        override val rsv2: Boolean = false
        override val rsv3: Boolean = false

        override fun install(config: Config.() -> Unit): WebSocketDeflateExtension =
            WebSocketDeflateExtension(Config().apply(config))
    }
}

private fun Frame.isCompressed(): Boolean = rsv1 && (this is Frame.Text || this is Frame.Binary)
