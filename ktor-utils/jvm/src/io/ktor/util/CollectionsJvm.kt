/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import java.util.*

/**
 * Wraps into an unmodifiable set
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.unmodifiable)
 */
public actual fun <T> Set<T>.unmodifiable(): Set<T> = Collections.unmodifiableSet(this)
