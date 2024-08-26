/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.backend

import io.ktor.util.network.*
import io.ktor.utils.io.*

/**
 * Represents a server incoming connection. Usually it is a TCP connection but potentially could be other transport.
 * @property input channel connected to incoming bytes end
 * @property output channel connected to outgoing bytes end
 * @property remoteAddress of the client (optional)
 * @property localAddress on which the client was accepted (optional)
 */
@InternalAPI
public class ServerIncomingConnection(
    public val input: ByteReadChannel,
    public val output: ByteWriteChannel,
    public val remoteAddress: NetworkAddress?,
    public val localAddress: NetworkAddress?
)
