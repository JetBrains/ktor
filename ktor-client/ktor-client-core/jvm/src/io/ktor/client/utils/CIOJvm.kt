/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import java.nio.*

/**
 * Singleton pool of [ByteBuffer] objects used for [HttpClient].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.utils.HttpClientDefaultPool)
 */
public val HttpClientDefaultPool: ByteBufferPool = ByteBufferPool()
