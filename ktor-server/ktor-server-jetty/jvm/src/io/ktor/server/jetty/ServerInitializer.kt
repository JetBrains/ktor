/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.jetty

import io.ktor.server.engine.*
import org.eclipse.jetty.alpn.server.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.server.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.ssl.*

internal fun Server.initializeServer(
    configuration: JettyApplicationEngineBase.Configuration
) {
    configuration.connectors.map { ktorConnector ->
        val httpConfig = HttpConfiguration().apply {
            sendServerVersion = false
            sendDateHeader = false

            if (ktorConnector.type == ConnectorType.HTTPS) {
                addCustomizer(SecureRequestCustomizer())
            }
        }.apply(configuration.httpConfiguration)

        var alpnAvailable = false
        var alpnConnectionFactory: ALPNServerConnectionFactory?
        var http2ConnectionFactory: HTTP2ServerConnectionFactory?

        try {
            alpnConnectionFactory = ALPNServerConnectionFactory().apply {
                defaultProtocol = HttpVersion.HTTP_1_1.asString()
            }
            http2ConnectionFactory = HTTP2ServerConnectionFactory(httpConfig)
            alpnAvailable = true
        } catch (t: Throwable) {
            // ALPN or HTTP/2 implemented is not available
            alpnConnectionFactory = null
            http2ConnectionFactory = null
        }

        val connectionFactories = when (ktorConnector.type) {
            ConnectorType.HTTP -> arrayOf(HttpConnectionFactory(httpConfig), HTTP2CServerConnectionFactory(httpConfig))
            ConnectorType.HTTPS -> arrayOf(
                SslConnectionFactory(
                    SslContextFactory.Server().apply {
                        if (alpnAvailable) {
                            cipherComparator = HTTP2Cipher.COMPARATOR
                            isUseCipherSuitesOrder = true
                        }

                        keyStore = (ktorConnector as EngineSSLConnectorConfig).keyStore
                        setKeyManagerPassword(String(ktorConnector.privateKeyPassword()))
                        setKeyStorePassword(String(ktorConnector.keyStorePassword()))

                        needClientAuth = when {
                            ktorConnector.trustStore != null -> {
                                trustStore = ktorConnector.trustStore
                                true
                            }
                            ktorConnector.trustStorePath != null -> {
                                trustStorePath = ktorConnector.trustStorePath!!.absolutePath
                                true
                            }
                            else -> false
                        }

                        ktorConnector.enabledProtocols?.let {
                            setIncludeProtocols(*it.toTypedArray())
                        }

                        addExcludeCipherSuites(
                            "SSL_RSA_WITH_DES_CBC_SHA",
                            "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                            "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA"
                        )
                    },
                    if (alpnAvailable) "alpn" else HttpVersion.HTTP_1_1.asString()
                ),
                alpnConnectionFactory,
                http2ConnectionFactory ?: HTTP2CServerConnectionFactory(httpConfig),
                HttpConnectionFactory(httpConfig)
            ).filterNotNull().toTypedArray()
            else -> throw IllegalArgumentException(
                "Connector type ${ktorConnector.type} is not supported by Jetty engine implementation"
            )
        }

        ServerConnector(this, *connectionFactories).apply {
            host = ktorConnector.host
            port = ktorConnector.port
            idleTimeout = configuration.idleTimeout.inWholeMilliseconds
        }
    }.forEach { this.addConnector(it) }
}
