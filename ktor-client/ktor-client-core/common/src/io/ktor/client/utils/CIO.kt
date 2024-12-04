/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

/**
 * Maximum number of buffers to be allocated in the [HttpClientDefaultPool].
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.utils.DEFAULT_HTTP_POOL_SIZE)
 */
public const val DEFAULT_HTTP_POOL_SIZE: Int = 1000

/**
 * Size of each buffer in the [HttpClientDefaultPool].
 */
public const val DEFAULT_HTTP_BUFFER_SIZE: Int = 4096
