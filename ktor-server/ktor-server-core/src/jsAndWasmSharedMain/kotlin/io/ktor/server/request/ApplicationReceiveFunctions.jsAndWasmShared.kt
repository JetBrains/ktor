/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.request

@PublishedApi
internal actual val DEFAULT_FORM_FIELD_LIMIT: Long
    get() = 50 * 1024 * 1024L
