/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.coroutines.*

/**
 * Ensures that when the given job is canceled, the ByteChannel is canceled with the same exception.
 */
public fun ByteChannel.attachJob(job: Job) {
    job.invokeOnCompletion {
        if (it != null) {
            cancel(it)
        }
    }
}

/**
 * Ensures that when the given job is canceled, the ByteChannel is canceled with the same exception.
 */
public fun ByteChannel.attachJob(job: ChannelJob) {
    attachJob(job.job)
}
