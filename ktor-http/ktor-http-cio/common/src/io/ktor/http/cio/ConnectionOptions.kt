/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.cio.internals.*

/**
 * Represents a parsed `Connection` header
 * @property close `true` for `Connection: close`
 * @property keepAlive `true` for `Connection: keep-alive`
 * @property upgrade `true` for `Connection: upgrade`
 * @property extraOptions a list of extra connection header options other than close, keep-alive and upgrade
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.cio.ConnectionOptions)
 */
public class ConnectionOptions(
    public val close: Boolean = false,
    public val keepAlive: Boolean = false,
    public val upgrade: Boolean = false,
    public val extraOptions: List<String> = emptyList()
) {
    public companion object {
        /**
         * An instance for `Connection: close`
         *
         * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.cio.ConnectionOptions.Companion.Close)
         */
        public val Close: ConnectionOptions = ConnectionOptions(close = true)

        /**
         * An instance for `Connection: keep-alive`
         */
        public val KeepAlive: ConnectionOptions = ConnectionOptions(keepAlive = true)

        /**
         * An instance for `Connection: upgrade`
         */
        public val Upgrade: ConnectionOptions = ConnectionOptions(upgrade = true)

        private val knownTypes = AsciiCharTree.build(
            listOf("close" to Close, "keep-alive" to KeepAlive, "upgrade" to Upgrade),
            { it.first.length },
            { t, idx -> t.first[idx] }
        )

        /**
         * Parse `Connection` header value
         */
        public fun parse(connection: CharSequence?): ConnectionOptions? {
            if (connection == null) return null
            val known = knownTypes.search(connection, lowerCase = true, stopPredicate = { _, _ -> false })
            if (known.size == 1) return known[0].second
            return parseSlow(connection)
        }

        private fun parseSlow(connection: CharSequence): ConnectionOptions {
            var idx = 0
            var start = 0
            val length = connection.length
            var connectionOptions: ConnectionOptions? = null
            var hopHeadersList: ArrayList<String>? = null

            while (idx < length) {
                do {
                    val ch = connection[idx]
                    if (ch != ' ' && ch != ',') {
                        start = idx
                        break
                    }
                    idx++
                } while (idx < length)

                while (idx < length) {
                    val ch = connection[idx]
                    if (ch == ' ' || ch == ',') break
                    idx++
                }

                val detected = knownTypes
                    .search(connection, start, idx, lowerCase = true, stopPredicate = { _, _ -> false })
                    .singleOrNull()
                when {
                    detected == null -> {
                        if (hopHeadersList == null) {
                            hopHeadersList = ArrayList()
                        }

                        hopHeadersList.add(connection.substring(start, idx))
                    }
                    connectionOptions == null -> connectionOptions = detected.second
                    else -> {
                        connectionOptions = ConnectionOptions(
                            close = connectionOptions.close || detected.second.close,
                            keepAlive = connectionOptions.keepAlive || detected.second.keepAlive,
                            upgrade = connectionOptions.upgrade || detected.second.upgrade,
                            extraOptions = emptyList()
                        )
                    }
                }
            }

            if (connectionOptions == null) connectionOptions = KeepAlive

            return if (hopHeadersList == null) {
                connectionOptions
            } else {
                ConnectionOptions(
                    connectionOptions.close,
                    connectionOptions.keepAlive,
                    connectionOptions.upgrade,
                    hopHeadersList
                )
            }
        }
    }

    override fun toString(): String = when {
        extraOptions.isEmpty() -> {
            when {
                close && !keepAlive && !upgrade -> "close"
                !close && keepAlive && !upgrade -> "keep-alive"
                !close && keepAlive && upgrade -> "keep-alive, Upgrade"
                else -> buildToString()
            }
        }
        else -> buildToString()
    }

    private fun buildToString() = buildString {
        val items = ArrayList<String>(extraOptions.size + 3)
        if (close) items.add("close")
        if (keepAlive) items.add("keep-alive")
        if (upgrade) items.add("Upgrade")

        if (extraOptions.isNotEmpty()) {
            items.addAll(extraOptions)
        }

        items.joinTo(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ConnectionOptions

        if (close != other.close) return false
        if (keepAlive != other.keepAlive) return false
        if (upgrade != other.upgrade) return false
        if (extraOptions != other.extraOptions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = close.hashCode()
        result = 31 * result + keepAlive.hashCode()
        result = 31 * result + upgrade.hashCode()
        result = 31 * result + extraOptions.hashCode()
        return result
    }
}
