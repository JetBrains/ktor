/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.http.parsing.*
import io.ktor.http.parsing.regex.*

/**
 * Check if [host] is IPv4 or IPv6 address.
 */
public fun hostIsIp(host: String): Boolean = IP_PARSER.match(host)

private val IPv4address = digits then "." then digits then "." then digits then "." then digits

private val IPv6address = "[" then atLeastOne(hex or ":") then "]"

private val IP_PARSER = (IPv4address or IPv6address).buildRegexParser()
