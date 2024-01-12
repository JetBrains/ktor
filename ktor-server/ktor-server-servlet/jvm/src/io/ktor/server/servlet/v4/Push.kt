/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet.v4

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import javax.servlet.http.*

@InternalAPI
@UseHttp2Push
public fun doPush(request: HttpServletRequest, builder: ResponsePushBuilder): Boolean {
    request.newPushBuilder()?.apply {
        this.method(builder.method.value)
        this.path(builder.url.encodedPath)

        val query = builder.url.buildString().substringAfter('?', "").takeIf { it.isNotEmpty() }
        if (query != null) {
            queryString(query)
        }

        push()
        return true
    }

    return false
}
