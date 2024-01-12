/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*

@Suppress("KDocMissingDocumentation", "DEPRECATION")
@InternalAPI
public abstract class ClientUpgradeContent : OutgoingContent.NoContent() {
    private val content: ByteChannel by lazy { ByteChannel() }

    public val output: ByteWriteChannel get() = content

    public suspend fun pipeTo(output: ByteWriteChannel) {
        content.copyAndClose(output)
    }

    public abstract fun verify(headers: Headers)
}
