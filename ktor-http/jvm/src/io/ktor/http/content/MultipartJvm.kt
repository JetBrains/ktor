/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.utils.io.jvm.javaio.*
import java.io.*

/**
 * Provides file item's content as an [InputStream]
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.streamProvider)
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("This API uses blocking InputStream. Please use provider() directly.")
public val PartData.FileItem.streamProvider: () -> InputStream get() = {
    provider().toInputStream()
}
