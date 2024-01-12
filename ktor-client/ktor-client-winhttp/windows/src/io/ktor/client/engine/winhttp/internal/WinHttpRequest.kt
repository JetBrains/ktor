/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.client.engine.winhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import platform.windows.*
import platform.windows.SECURITY_FLAG_IGNORE_CERT_CN_INVALID
import platform.windows.SECURITY_FLAG_IGNORE_CERT_DATE_INVALID
import platform.windows.SECURITY_FLAG_IGNORE_UNKNOWN_CA
import platform.winhttp.*
import kotlin.coroutines.*

@OptIn(ExperimentalForeignApi::class)
internal class WinHttpRequest @OptIn(ExperimentalForeignApi::class) constructor(
    hSession: COpaquePointer,
    data: HttpRequestData,
    config: WinHttpClientEngineConfig
) : Closeable {
    private val connect: WinHttpConnect

    @OptIn(ExperimentalForeignApi::class)
    private val hRequest: COpaquePointer
    private val closed = atomic(false)
    private val requestClosed = atomic(false)

    @OptIn(ExperimentalForeignApi::class)
    private val connectReference: StableRef<WinHttpConnect>

    init {
        val hConnect = WinHttpConnect(hSession, data.url.host, data.url.port.convert(), 0.convert())
            ?: throw getWinHttpException("Unable to create connection")
        connect = WinHttpConnect(hConnect)
        connectReference = StableRef.create(connect)

        // Try using explicitly specified HTTP protocol version
        val protocolVersion = config.protocolVersion
        val httpVersion = when (protocolVersion) {
            HttpProtocolVersion.HTTP_1_0 -> protocolVersion.toString()
            HttpProtocolVersion.HTTP_1_1 -> protocolVersion.toString()
            else -> null
        }

        hRequest = connect.openRequest(data.method, data.url, httpVersion)
            ?: throw getWinHttpException("Unable to open request")

        configureFeatures()

        enableHttpProtocols(protocolVersion)

        if (!config.sslVerify) {
            disableTlsVerification()
        }

        configureStatusCallback(enable = true)
    }

    fun upgradeToWebSocket() {
        if (WinHttpSetOption(hRequest, WINHTTP_OPTION_UPGRADE_TO_WEB_SOCKET.convert(), null, 0.convert()) == 0) {
            throw getWinHttpException("Unable to request WebSocket upgrade")
        }
    }

    /**
     * Sends request with headers to remote server. WinHTTP executes request asynchronously,
     * where to receive notifications about operations used callback function with context.
     *
     * @param headers is request headers.
     */
    suspend fun sendRequest(headers: Map<String, String>) {
        return closeableCoroutine(connect, ERROR_FAILED_TO_SEND_REQUEST) { continuation ->
            val headersString = headers.map {
                "${it.key}: ${it.value}"
            }.joinToString("\r\n")

            connect.on(WinHttpCallbackStatus.SendRequestComplete) { _, _ ->
                continuation.resume(Unit)
            }

            // Send request
            val statePtr = connectReference.asCPointer().rawValue.toLong()
            if (WinHttpSendRequest(
                    hRequest,
                    headersString,
                    headersString.length.convert(),
                    WINHTTP_NO_REQUEST_DATA,
                    0.convert(),
                    WINHTTP_IGNORE_REQUEST_TOTAL_LENGTH.convert(),
                    statePtr.convert()
                ) == 0
            ) {
                throw getWinHttpException(ERROR_FAILED_TO_SEND_REQUEST)
            }
        }
    }

    /**
     * Writes request body data.
     *
     * @param buffer is source buffer.
     * @param length is a number of bytes to write.
     */
    suspend fun writeData(buffer: Pinned<ByteArray>, length: Int = buffer.get().size) {
        return closeableCoroutine(connect, ERROR_FAILED_TO_WRITE_REQUEST) { continuation ->
            connect.on(WinHttpCallbackStatus.WriteComplete) { _, _ ->
                continuation.resume(Unit)
            }

            if (WinHttpWriteData(hRequest, buffer.addressOf(0), length.convert(), null) == 0) {
                throw getWinHttpException(ERROR_FAILED_TO_WRITE_REQUEST)
            }
        }
    }

    /**
     * Request server to send a response.
     */
    suspend fun getResponse(): WinHttpResponseData {
        return closeableCoroutine(connect, ERROR_FAILED_TO_RECEIVE_RESPONSE) { continuation ->
            connect.on(WinHttpCallbackStatus.HeadersAvailable) { _, _ ->
                try {
                    val responseData = getResponseData()
                    continuation.resume(responseData)
                } catch (cause: Exception) {
                    continuation.resumeWithException(cause)
                }
            }

            if (WinHttpReceiveResponse(hRequest, null) == 0) {
                throw getWinHttpException(ERROR_FAILED_TO_RECEIVE_RESPONSE)
            }
        }
    }

    /**
     * Construct a body after receiving response headers.
     */
    private fun getResponseData() = memScoped {
        val dwStatusCode = alloc<UIntVar>()
        val dwSize = alloc<UIntVar> {
            value = UINT_SIZE
        }

        // Get status code
        val statusCodeFlags = WINHTTP_QUERY_STATUS_CODE or WINHTTP_QUERY_FLAG_NUMBER
        if (WinHttpQueryHeaders(hRequest, statusCodeFlags.convert(), null, dwStatusCode.ptr, dwSize.ptr, null) == 0) {
            throw getWinHttpException("Failed to query status code")
        }

        val httpVersion = if (isHttp2Response()) {
            "HTTP/2.0"
        } else {
            getHeader(WINHTTP_QUERY_VERSION)
        }

        WinHttpResponseData(
            statusCode = dwStatusCode.value.convert(),
            httpProtocol = httpVersion,
            headers = getHeader(WINHTTP_QUERY_RAW_HEADERS_CRLF)
        )
    }

    /**
     * Requests a number of available bytes of response body.
     */
    suspend fun queryDataAvailable(): Int {
        return closeableCoroutine(connect, ERROR_FAILED_TO_QUERY_DATA) { continuation ->
            connect.on(WinHttpCallbackStatus.DataAvailable) { statusInfo, _ ->
                val dataSize = statusInfo!!.reinterpret<ULongVar>().pointed.value
                continuation.resume(dataSize.convert())
            }

            if (WinHttpQueryDataAvailable(hRequest, null) == 0) {
                throw getWinHttpException(ERROR_FAILED_TO_QUERY_DATA)
            }
        }
    }

    /**
     * Reads a response body into buffer.
     *
     * @param buffer is target buffer.
     * @param length is number of bytes to read.
     */
    suspend fun readData(buffer: Pinned<ByteArray>, length: Int): Int {
        return closeableCoroutine(connect, ERROR_FAILED_TO_READ_RESPONSE) { continuation ->
            connect.on(WinHttpCallbackStatus.ReadComplete) { _, statusInfoLength ->
                continuation.resume(statusInfoLength.convert())
            }

            if (WinHttpReadData(hRequest, buffer.addressOf(0), length.convert(), null) == 0) {
                throw getWinHttpException(ERROR_FAILED_TO_READ_RESPONSE)
            }
        }
    }

    /**
     * Creates a new WebSocket.
     *
     * @param callContext is call context.
     */
    fun createWebSocket(callContext: CoroutineContext): WinHttpWebSocket {
        val statePtr = connectReference.asCPointer().rawValue.toLong()
        val hWebsocket = WinHttpWebSocketCompleteUpgrade(hRequest, statePtr.convert())
            ?: throw getWinHttpException("Unable to upgrade websocket")

        return WinHttpWebSocket(hWebsocket, connect, callContext).also {
            closeRequest()
        }
    }

    /**
     * Disables built-in features which are handled by Ktor client.
     */
    private fun configureFeatures() = memScoped {
        val options = alloc<DWORDVar> {
            value = (WINHTTP_DISABLE_COOKIES or WINHTTP_DISABLE_REDIRECTS).convert()
        }

        if (WinHttpSetOption(
                hRequest,
                WINHTTP_OPTION_DISABLE_FEATURE.convert(),
                options.ptr,
                sizeOf<DWORDVar>().convert()
            ) == 0
        ) {
            throw getWinHttpException("Unable to configure request options")
        }
    }

    /**
     * Receive status callbacks about all operations.
     */
    private fun configureStatusCallback(enable: Boolean) = memScoped {
        val notifications = WINHTTP_CALLBACK_FLAG_ALL_COMPLETIONS.convert<UInt>()
        val callback = if (enable) {
            staticCFunction(::winHttpCallback)
        } else {
            null
        }

        val oldStatusCallback = WinHttpSetStatusCallback(hRequest, callback, notifications, 0.convert())
        if (oldStatusCallback?.rawValue?.toLong() == WINHTTP_INVALID_STATUS_CALLBACK) {
            val errorCode = GetLastError()
            if (errorCode != ERROR_INVALID_HANDLE) {
                throw getWinHttpException("Unable to set request callback", errorCode)
            }
        }
    }

    /**
     * Enables requested HTTP protocols.
     *
     * @param protocolVersion is required protocol version.
     */
    private fun enableHttpProtocols(protocolVersion: HttpProtocolVersion) = memScoped {
        if (protocolVersion != HttpProtocolVersion.HTTP_2_0) return@memScoped
        val flags = alloc<UIntVar> {
            value = WINHTTP_PROTOCOL_FLAG_HTTP2.convert()
        }
        WinHttpSetOption(hRequest, WINHTTP_OPTION_ENABLE_HTTP_PROTOCOL, flags.ptr, UINT_SIZE)
    }

    /**
     * Disables TLS verification for testing purposes.
     */
    private fun disableTlsVerification() = memScoped {
        val flags = alloc<UIntVar> {
            value = (
                SECURITY_FLAG_IGNORE_UNKNOWN_CA or
                    SECURITY_FLAG_IGNORE_CERT_WRONG_USAGE or
                    SECURITY_FLAG_IGNORE_CERT_CN_INVALID or
                    SECURITY_FLAG_IGNORE_CERT_DATE_INVALID
                ).convert()
        }
        if (WinHttpSetOption(hRequest, WINHTTP_OPTION_SECURITY_FLAGS.convert(), flags.ptr, UINT_SIZE) == 0) {
            throw getWinHttpException("Unable to disable TLS verification")
        }
    }

    internal fun isChunked(data: HttpRequestData): Boolean {
        if (data.body is OutgoingContent.NoContent) return false
        val contentLength = data.body.contentLength ?: data.body.headers[HttpHeaders.ContentLength]?.toLong()
        return contentLength == null ||
            data.headers[HttpHeaders.TransferEncoding] == "chunked" ||
            data.body.headers[HttpHeaders.TransferEncoding] == "chunked"
    }

    /**
     * Gets a string length in bytes.
     */
    private fun getLength(dwSize: UIntVar) = (dwSize.value / sizeOf<ShortVar>().convert()).convert<Int>()

    /**
     * Returns a header value.
     *
     * @param headerId is header identifier.
     */
    private fun getHeader(headerId: Int): String = memScoped {
        val dwSize = alloc<UIntVar>()

        // Get header length
        if (WinHttpQueryHeaders(hRequest, headerId.convert(), null, null, dwSize.ptr, null) == 0) {
            val errorCode = GetLastError()
            if (errorCode != ERROR_INSUFFICIENT_BUFFER.convert<UInt>()) {
                throw getWinHttpException("Unable to query response headers length")
            }
        }

        // Read header into buffer
        val buffer = allocArray<ShortVar>(getLength(dwSize) + 1)
        if (WinHttpQueryHeaders(hRequest, headerId.convert(), null, buffer, dwSize.ptr, null) == 0) {
            throw getWinHttpException("Unable to query response headers")
        }

        buffer.toKStringFromUtf16()
    }

    /**
     * Gets a HTTP protocol version from server response.
     */
    private fun isHttp2Response() = memScoped {
        val flags = alloc<UIntVar>()
        val dwSize = alloc<UIntVar> {
            value = UINT_SIZE
        }
        if (WinHttpQueryOption(hRequest, WINHTTP_OPTION_HTTP_PROTOCOL_USED, flags.ptr, dwSize.ptr) != 0) {
            if ((flags.value.convert<Int>() and WINHTTP_PROTOCOL_FLAG_HTTP2) != 0) {
                return true
            }
        }
        false
    }

    private fun closeRequest() {
        if (!requestClosed.compareAndSet(expect = false, update = true)) return

        configureStatusCallback(enable = false)
        WinHttpCloseHandle(hRequest)
    }

    override fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return

        closeRequest()
        connect.close()

        connectReference.dispose()
    }

    companion object {
        private const val WINHTTP_OPTION_ENABLE_HTTP_PROTOCOL = 133u
        private const val WINHTTP_OPTION_HTTP_PROTOCOL_USED = 134u
        private const val WINHTTP_PROTOCOL_FLAG_HTTP2 = 0x1
        private const val WINHTTP_INVALID_STATUS_CALLBACK: Long = -1
        private const val ERROR_INVALID_HANDLE = 0x6u
        private val WINHTTP_NO_REQUEST_DATA = null

        private val UINT_SIZE: UInt = sizeOf<UIntVar>().convert()

        private const val ERROR_FAILED_TO_SEND_REQUEST = "Failed to send request"
        private const val ERROR_FAILED_TO_WRITE_REQUEST = "Failed to write request data"
        private const val ERROR_FAILED_TO_RECEIVE_RESPONSE = "Failed to receive response"
        private const val ERROR_FAILED_TO_QUERY_DATA = "Failed to query data length"
        private const val ERROR_FAILED_TO_READ_RESPONSE = "Failed to read response data"
    }
}
