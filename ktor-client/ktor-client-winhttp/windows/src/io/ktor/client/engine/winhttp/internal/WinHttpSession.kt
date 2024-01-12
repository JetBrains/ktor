/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.client.engine.*
import io.ktor.client.engine.winhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import platform.winhttp.*

@OptIn(ExperimentalForeignApi::class)
internal class WinHttpSession(private val config: WinHttpClientEngineConfig) : Closeable {

    @OptIn(ExperimentalForeignApi::class)
    private var hSession: COpaquePointer
    private val closed = atomic(false)
    private val timeoutConfigured = atomic(false)

    init {
        hSession = WinHttpOpen(
            WINHTTP_NO_USER_AGENT,
            WINHTTP_ACCESS_TYPE_DEFAULT_PROXY.convert(),
            WINHTTP_NO_PROXY_NAME,
            WINHTTP_NO_PROXY_BYPASS,
            WINHTTP_FLAG_ASYNC.convert()
        ) ?: throw getWinHttpException("Unable to create session")

        setSecurityProtocols(config.securityProtocols)

        config.proxy?.let { proxy ->
            setProxy(proxy)
        }
    }

    fun createRequest(data: HttpRequestData): WinHttpRequest {
        configureTimeouts(data)

        return WinHttpRequest(hSession, data, config)
    }

    private fun configureTimeouts(data: HttpRequestData) {
        if (!timeoutConfigured.compareAndSet(expect = false, update = true)) return

        val resolveTimeout = 10_000
        var connectTimeout = 60_000
        var sendTimeout = 30_000
        var receiveTimeout = 30_000

        data.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeoutExtension ->
            timeoutExtension.connectTimeoutMillis?.let { value ->
                connectTimeout = value.toInt()
            }
            timeoutExtension.socketTimeoutMillis?.let { value ->
                sendTimeout = value.toInt()
                receiveTimeout = value.toInt()
            }
        }

        setTimeouts(resolveTimeout, connectTimeout, sendTimeout, receiveTimeout)
    }

    private fun setSecurityProtocols(protocol: WinHttpSecurityProtocol) = memScoped {
        val options = alloc<UIntVar> {
            value = protocol.value.convert()
        }
        val dwSize = sizeOf<UIntVar>().convert<UInt>()
        WinHttpSetOption(hSession, WINHTTP_OPTION_SECURE_PROTOCOLS.convert(), options.ptr, dwSize)
    }

    private fun setTimeouts(resolveTimeout: Int, connectTimeout: Int, sendTimeout: Int, receiveTimeout: Int) {
        if (WinHttpSetTimeouts(hSession, resolveTimeout, connectTimeout, sendTimeout, receiveTimeout) == 0) {
            throw getWinHttpException("Unable to set session timeouts")
        }
    }

    private fun setProxy(proxy: ProxyConfig) = memScoped {
        when (val type = proxy.type) {
            ProxyType.HTTP -> {
                val proxyInfo = alloc<WINHTTP_PROXY_INFO> {
                    dwAccessType = WINHTTP_ACCESS_TYPE_NAMED_PROXY.convert()
                    lpszProxy = proxy.url.toString().wcstr.ptr
                }

                if (WinHttpSetOption(
                        hSession,
                        WINHTTP_OPTION_PROXY.convert(),
                        proxyInfo.ptr,
                        sizeOf<WINHTTP_PROXY_INFO>().convert()
                    ) == 0
                ) {
                    throw getWinHttpException("Unable to set proxy")
                }
            }

            else -> throw IllegalStateException("Proxy of type $type is unsupported by WinHTTP engine.")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return

        WinHttpCloseHandle(hSession)
    }

    companion object {
        private val WINHTTP_NO_PROXY_NAME = null
        private val WINHTTP_NO_PROXY_BYPASS = null
        private val WINHTTP_NO_USER_AGENT = null
    }
}
