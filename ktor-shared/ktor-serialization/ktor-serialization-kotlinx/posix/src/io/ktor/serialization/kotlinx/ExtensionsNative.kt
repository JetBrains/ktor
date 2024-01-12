/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.utils.io.*

private val _providers: MutableList<KotlinxSerializationExtensionProvider> = mutableListOf()
internal actual val providers: List<KotlinxSerializationExtensionProvider> = _providers

@InternalAPI
public fun addExtensionProvider(provider: KotlinxSerializationExtensionProvider) {
    _providers.add(provider)
}
