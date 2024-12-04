/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import io.ktor.util.date.*
import java.util.*

/**
 * This version passes the given [lastModified] date through the client provided
 * http conditional headers If-Modified-Since and If-Unmodified-Since.
 *
 * Notice the second precision so it may work wrong if there were few changes during the same second.
 *
 * For better behaviour use etag instead
 *
 * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.28 and
 *  https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.25
 *
 *  @param lastModified of the current content, for example file's last modified date
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.content.LastModifiedVersion)
 */
public fun LastModifiedVersion(lastModified: Date): LastModifiedVersion =
    LastModifiedVersion(GMTDate(lastModified.time))
