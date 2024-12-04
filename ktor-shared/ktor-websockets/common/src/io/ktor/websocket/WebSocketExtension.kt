/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.websocket

import io.ktor.util.*

private typealias ExtensionInstaller = () -> WebSocketExtension<*>

/**
 * A factory that defines a WebSocket extension. The factory is used in pair with the
 * [WebSocketExtensionsConfig.install] method to install the WebSocket extension in client or server.
 *
 * Usually this interface is implemented in `companion object` of the origin [WebSocketExtension].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.WebSocketExtensionFactory)
 */
public interface WebSocketExtensionFactory<ConfigType : Any, ExtensionType : WebSocketExtension<ConfigType>> {
    /**
     * A key used to locate an extension.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.WebSocketExtensionFactory.key)
     */
    public val key: AttributeKey<ExtensionType>

    /**
     * The first extension bit used by the current extension.
     *
     * This flag is used to detect extension conflicts: only one plugin with the enabled flag is allowed.
     * To set the flag value, consult with specification of the extension you're using.
     */
    public val rsv1: Boolean

    /**
     * A second extension bit used by the current extension.
     *
     * This flag is used to detect extension conflicts: only one plugin with the enabled flag is allowed.
     * To set the flag value, consult with specification of the extension you're using.
     */
    public val rsv2: Boolean

    /**
     * A third extension bit used by the current extension.
     *
     * This flag is used to detect extension conflicts: only one plugin with enabled flag is allowed.
     * To set the flag value, consult with specification of the extension you're using.
     */
    public val rsv3: Boolean

    /**
     * Creates an extension instance using [config] block. The extension instance is created for each WebSocket request.
     */
    public fun install(config: ConfigType.() -> Unit): ExtensionType
}

/**
 * A WebSocket extension instance.
 * This instance is created for each WebSocket request, for every installed extension by [WebSocketExtensionFactory].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.WebSocketExtension)
 */
public interface WebSocketExtension<ConfigType : Any> {

    /**
     * Reference to the [WebSocketExtensionFactory], which produced this extension.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.WebSocketExtension.factory)
     */
    public val factory: WebSocketExtensionFactory<ConfigType, out WebSocketExtension<ConfigType>>

    /**
     * List of WebSocket extension protocols which will be sent by client in headers.
     * They are required to inform server that client wants to negotiate the current extension.
     */
    public val protocols: List<WebSocketExtensionHeader>

    /**
     * This method is called only for the client, when it receives the WebSocket upgrade response.
     *
     * @param negotiatedProtocols contains list of negotiated extensions from the server (can be empty).
     *
     * It's up to extension to decide if it should be used or not.
     * @return `true` if the extension should be used by the client.
     */
    public fun clientNegotiation(negotiatedProtocols: List<WebSocketExtensionHeader>): Boolean

    /**
     * This method is called only for the server, when it receives WebSocket session.
     *
     * @param requestedProtocols contains list of requested extensions from the client (can be empty).
     *
     * @return list of protocols (with parameters), which server prefers to use for the current client request.
     */
    public fun serverNegotiation(
        requestedProtocols: List<WebSocketExtensionHeader>
    ): List<WebSocketExtensionHeader>

    /**
     * This method is called on each outgoing frame and handle it before sending.
     */
    public fun processOutgoingFrame(frame: Frame): Frame

    /**
     * This method is called on each incoming frame before handling it in WebSocket session.
     */
    public fun processIncomingFrame(frame: Frame): Frame
}

/**
 * Extensions configuration for the WebSocket client and server plugins.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.WebSocketExtensionsConfig)
 */
public class WebSocketExtensionsConfig {
    private val installers: MutableList<ExtensionInstaller> = mutableListOf()
    private val rcv: Array<Boolean> = arrayOf(false, false, false)

    /**
     * Installs the provided [extension] using [config]. Every extension is processed in order of installation.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.websocket.WebSocketExtensionsConfig.install)
     */
    public fun <ConfigType : Any> install(
        extension: WebSocketExtensionFactory<ConfigType, *>,
        config: ConfigType.() -> Unit = {}
    ) {
        checkConflicts(extension)
        installers += { extension.install(config) }
    }

    /**
     * Instantiates all installed extensions.
     */
    public fun build(): List<WebSocketExtension<*>> = installers.map { it() }

    private fun checkConflicts(extensionFactory: WebSocketExtensionFactory<*, *>) {
        var hasConflict = extensionFactory.rsv1 && rcv[1]
        hasConflict = hasConflict || extensionFactory.rsv2 && rcv[2]
        hasConflict = hasConflict || extensionFactory.rsv3 && rcv[3]

        check(!hasConflict) { "Failed to install extension. Please check configured extensions for conflicts." }
    }
}
