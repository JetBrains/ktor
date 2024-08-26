/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.response

import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

private val ResponseTypeAttributeKey: AttributeKey<TypeInfo> = AttributeKey("ResponseTypeAttributeKey")

/**
 * A type of response object that is passed in the [respond] function.
 * Can be useful for custom serializations.
 */
public var ApplicationResponse.responseType: TypeInfo?
    get() = call.attributes.getOrNull(ResponseTypeAttributeKey)

    @InternalAPI set(value) {
        if (value != null) {
            call.attributes.put(ResponseTypeAttributeKey, value)
        } else {
            call.attributes.remove(ResponseTypeAttributeKey)
        }
    }
