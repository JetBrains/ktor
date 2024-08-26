/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

private fun locationOrigin(): String = js(
    """function() {
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
    return origin && origin != "null" ? origin : "http://localhost"    
}()"""
)

/**
 * Hostname of current origin.
 *
 * It uses "localhost" for all platforms except js.
 */
public actual val URLBuilder.Companion.origin: String
    get() = when {
        PlatformUtils.IS_BROWSER -> locationOrigin()
        else -> "http://localhost"
    }
