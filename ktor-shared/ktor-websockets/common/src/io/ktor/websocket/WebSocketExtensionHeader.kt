/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.websocket

/**
 * A parsed `Sec-WebSocket-Accept` header item representation.
 *
 * @param name is extension name.
 * @param parameters is list of extension parameters.
 */
public class WebSocketExtensionHeader(public val name: String, public val parameters: List<String>) {

    /**
     * Parses parameters keys and values.
     */
    public fun parseParameters(): Sequence<Pair<String, String>> = parameters.asSequence().map {
        val equalsIndex = it.indexOf('=')
        if (equalsIndex < 0) return@map it to ""

        val key = it.substring(0 until equalsIndex)
        val value = if (equalsIndex + 1 < it.length) it.substring(equalsIndex + 1) else ""

        key to value
    }

    override fun toString(): String = "$name ${parametersToString()}"

    private fun parametersToString(): String =
        if (parameters.isEmpty()) "" else ", ${parameters.joinToString(",")}"
}

/**
 * Parses the `Sec-WebSocket-Accept` header.
 */
public fun parseWebSocketExtensions(value: String): List<WebSocketExtensionHeader> = value
    .split(",")
    .map { it ->
        val extension = it.split(";")
        val name = extension.first().trim()
        val parameters = extension.drop(1).map { it.trim() }
        WebSocketExtensionHeader(name, parameters)
    }
