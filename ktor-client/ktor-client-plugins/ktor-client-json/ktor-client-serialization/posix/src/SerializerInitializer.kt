/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.kotlinx.serializer

import io.ktor.client.plugins.json.*
import io.ktor.utils.io.*

@OptIn(ExperimentalStdlibApi::class)
@Suppress("unused", "DEPRECATION")
@EagerInitialization
private val initHook = SerializerInitializer

@Suppress("DEPRECATION_ERROR")
@OptIn(InternalAPI::class)
private object SerializerInitializer {
    init {
        serializers.add(KotlinxSerializer())
    }
}
