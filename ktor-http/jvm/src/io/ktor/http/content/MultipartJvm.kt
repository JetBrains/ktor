/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import java.io.*

/**
 * Provides file item's content as an [InputStream]
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("This API uses blocking InputStream. Please use provider() directly.")
public val PartData.FileItem.streamProvider: () -> InputStream get() = error(
    "streamProvider is deprecated. Use provider() instead"
)
