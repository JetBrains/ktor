/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.client.network.sockets.*
import kotlinx.cinterop.*
import platform.windows.*
import platform.winhttp.*

@OptIn(ExperimentalForeignApi::class)
private val winHttpModuleHandle by lazy {
    GetModuleHandleW("winhttp.dll")
}

@OptIn(ExperimentalForeignApi::class)
private val languageId = makeLanguageId(LANG_NEUTRAL.convert(), SUBLANG_DEFAULT.convert())

@OptIn(ExperimentalForeignApi::class)
private val ERROR_INSUFFICIENT_BUFFER: UInt = platform.windows.ERROR_INSUFFICIENT_BUFFER.convert()

/**
 * Creates an exception from last WinAPI error.
 */
internal fun getWinHttpException(message: String): Exception {
    val errorCode = GetLastError()
    return getWinHttpException(message, errorCode)
}

/**
 * Creates an exception from WinAPI error code.
 */
internal fun getWinHttpException(message: String, errorCode: UInt): Exception {
    val hResult = getHResultFromWin32Error(errorCode)
    val errorMessage = getErrorMessage(errorCode).trimEnd('.')
    val cause = "$message: $errorMessage. Error $errorCode (0x${hResult.toString(16)})"

    return if (errorCode.toInt() == ERROR_WINHTTP_TIMEOUT) {
        ConnectTimeoutException(cause)
    } else {
        IllegalStateException(cause)
    }
}

/**
 * Creates an error message from WinAPI error code.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun getErrorMessage(errorCode: UInt): String {
    return formatMessage(errorCode, winHttpModuleHandle)
        ?: formatMessage(errorCode)
        ?: "Unknown error"
}

/**
 * Formats error code into human readable error message.
 *
 * @param errorCode is error code.
 * @param moduleHandle is DLL handle to look for message.
 */
@OptIn(ExperimentalForeignApi::class)
private fun formatMessage(errorCode: UInt, moduleHandle: HMODULE? = null): String? = memScoped {
    val formatSourceFlag = if (moduleHandle != null) {
        FORMAT_MESSAGE_FROM_HMODULE
    } else {
        FORMAT_MESSAGE_FROM_SYSTEM
    }

    // Try reading error message into allocated buffer
    var formatFlags = FORMAT_MESSAGE_IGNORE_INSERTS or FORMAT_MESSAGE_ARGUMENT_ARRAY or formatSourceFlag
    val bufferSize = 256
    val buffer = allocArray<UShortVar>(bufferSize)

    var readChars = FormatMessageW(
        formatFlags.convert(),
        moduleHandle,
        errorCode,
        languageId,
        buffer.reinterpret(),
        bufferSize.convert(),
        null
    )

    // Read message from buffer
    if (readChars > 0u) {
        return@memScoped buffer.toKStringFromUtf16(readChars.convert())
    }

    if (GetLastError() != ERROR_INSUFFICIENT_BUFFER) {
        return@memScoped null
    }

    // If allocated buffer is too small, try to request buffer allocation
    formatFlags = formatFlags or FORMAT_MESSAGE_ALLOCATE_BUFFER

    val bufferPtr = alloc<CPointerVar<UShortVar>>()
    @Suppress("INFERRED_TYPE_VARIABLE_INTO_EMPTY_INTERSECTION_ERROR")
    readChars = FormatMessageW(
        formatFlags.convert(),
        moduleHandle,
        errorCode,
        languageId,
        bufferPtr.reinterpret(),
        0.convert(),
        null
    )

    return try {
        if (readChars > 0u) {
            bufferPtr.value?.toKStringFromUtf16(readChars.convert())
        } else {
            null
        }
    } finally {
        @Suppress("INFERRED_TYPE_VARIABLE_INTO_EMPTY_INTERSECTION_ERROR")
        LocalFree(bufferPtr.reinterpret())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<UShortVar>.toKStringFromUtf16(size: Int): String {
    val nativeBytes = this

    var length: Int = size
    while (length > 0 && nativeBytes[length - 1] <= 0x20u) {
        length--
    }

    val chars = CharArray(length) { index ->
        val nativeByte = nativeBytes[index].toInt()
        val char = nativeByte.toChar()
        char
    }

    return chars.concatToString()
}

/**
 * Implements HRESULT_FROM_WIN32 macro.
 */
private fun getHResultFromWin32Error(errorCode: UInt): UInt {
    return if ((errorCode and 0x80000000u) == 0x80000000u) {
        errorCode
    } else {
        (errorCode and 0x0000FFFFu) or 0x80070000u
    }
}

/**
 * Implements MAKELANGID macro.
 */
private fun makeLanguageId(primaryLanguageId: UInt, subLanguageId: UInt): UInt {
    return ((subLanguageId) shl 10) or primaryLanguageId
}
