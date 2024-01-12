/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

/**
 * Hostname of current origin.
 *
 * It uses "localhost" for all platforms except js.
 *
 * Note that not all platforms have location set. React Native platofrms expose window without a location.
 */
public actual val URLBuilder.Companion.origin: String
    get() = when (PlatformUtils.platform) {
        Platform.Browser -> {
            js(
                """
                var tmpLocation = null
                if (typeof window !== 'undefined') {
                  tmpLocation = window.location
                } else if (typeof self !== 'undefined') {
                  tmpLocation = self.location
                }
                var origin = ""
                if (tmpLocation) {
                  origin = tmpLocation.origin
                }
                origin && origin != "null" ? origin : "http://localhost"
                """
            ) as String
        }
        else -> "http://localhost"
    }
