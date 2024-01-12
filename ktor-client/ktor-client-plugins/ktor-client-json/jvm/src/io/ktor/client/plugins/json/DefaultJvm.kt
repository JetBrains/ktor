/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.json

import java.util.*

@Suppress("DEPRECATION_ERROR")
public actual fun defaultSerializer(): JsonSerializer {
    val serializers = ServiceLoader.load(JsonSerializer::class.java)
        .toList()

    if (serializers.isEmpty()) {
        error(
            """Fail to find serializer. Consider to add one of the following dependencies: 
 - ktor-client-gson
 - ktor-client-json
 - ktor-client-serialization"""
        )
    }

    return serializers.maxByOrNull { it::javaClass.name }!!
}
