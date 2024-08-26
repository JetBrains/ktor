/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.internal

/**
 * Internal helper for setting cause on [Throwable] in MPP
 */
public actual fun Throwable.initCauseBridge(cause: Throwable) {}
