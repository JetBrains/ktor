/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*
import kotlinx.io.*

@Suppress("KDocMissingDocumentation", "DEPRECATION")
internal class TLSRecord(
    val type: TLSRecordType = TLSRecordType.Handshake,
    val version: TLSVersion = TLSVersion.TLS12,
    val packet: Source = ByteReadPacketEmpty
)

internal class TLSHandshake {
    var type: TLSHandshakeType = TLSHandshakeType.HelloRequest
    var packet = ByteReadPacketEmpty
}
