/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.*
import org.slf4j.*
import org.slf4j.helpers.NOPLoggerFactory

public actual val Logger.Companion.DEFAULT: Logger
    get() = object : Logger {
        private val delegate = LoggerFactory.getLogger(HttpClient::class.java)!!
        override fun log(message: String) {
            delegate.info(message)
        }
    }

/**
 * Android [Logger]: Logs to the Logcat on Android if the SLF4J provider isn't found.
 * Otherwise, uses the [Logger.Companion.DEFAULT].
 * Breaks up long log messages that would be truncated by Android's max log
 * length of 4068 characters.
 */
public val Logger.Companion.ANDROID: Logger by lazy { getAndroidLogger() }

private fun getAndroidLogger(): Logger {
    val logger = Logger.DEFAULT

    val logClass = try {
        Class.forName("android.util.Log")
    } catch (_: ClassNotFoundException) {
        return MessageLengthLimitingLogger(delegate = logger)
    }

    if (LoggerFactory.getILoggerFactory() !is NOPLoggerFactory) {
        return MessageLengthLimitingLogger(delegate = logger)
    }

    return MessageLengthLimitingLogger(delegate = LogcatLogger(logClass, logger))
}

private class LogcatLogger(logClass: Class<*>, private val fallback: Logger) : Logger {
    private val tag = "Ktor Client"

    private val method = try {
        logClass.getDeclaredMethod("i", String::class.java, String::class.java)
    } catch (_: Throwable) {
        null
    }
    override fun log(message: String) {
        if (method == null) {
            fallback.log(message)
            return
        }

        try {
            method.invoke(null, tag, message)
        } catch (_: Throwable) {
            fallback.log(message)
        }
    }
}

/**
 * A [Logger] that breaks up log messages into multiple logs no longer than [maxLength]
 * @property maxLength max length allowed for a log message
 * @property minLength if log message is longer than [maxLength], attempt to break the log
 * message at a new line between [minLength] and [maxLength] if one exists
 */
public class MessageLengthLimitingLogger(
    private val maxLength: Int = 4000,
    private val minLength: Int = 3000,
    private val delegate: Logger = Logger.DEFAULT
) : Logger {
    override fun log(message: String) {
        logLong(message)
    }

    private tailrec fun logLong(message: String) {
        // String to be logged is longer than the max...
        if (message.length > maxLength) {
            var msgSubstring = message.substring(0, maxLength)
            var msgSubstringEndIndex = maxLength

            // Try to find a substring break at a newline char.
            msgSubstring.lastIndexOf('\n').let { lastIndex ->
                if (lastIndex >= minLength) {
                    msgSubstring = msgSubstring.substring(0, lastIndex)
                    // skip over new line char
                    msgSubstringEndIndex = lastIndex + 1
                }
            }

            // Log the substring.
            delegate.log(msgSubstring)

            // Recursively log the remainder.
            logLong(message.substring(msgSubstringEndIndex))
        } else {
            delegate.log(message)
        } // String to be logged is shorter than the max...
    }
}
