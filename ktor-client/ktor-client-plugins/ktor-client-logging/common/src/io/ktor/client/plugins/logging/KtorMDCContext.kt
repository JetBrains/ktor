/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.utils.io.*
import kotlin.coroutines.*

@InternalAPI
public expect fun MDCContext(): CoroutineContext.Element
