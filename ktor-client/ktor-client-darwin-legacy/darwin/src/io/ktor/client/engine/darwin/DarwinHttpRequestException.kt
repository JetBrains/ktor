/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import kotlinx.io.*
import platform.Foundation.*

public class DarwinHttpRequestException(public val origin: NSError) : IOException("Exception in http request: $origin")
