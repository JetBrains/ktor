/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.converters

import kotlin.reflect.*

internal actual fun platformDefaultFromValues(value: String, klass: KClass<*>): Any? = null
internal actual fun platformDefaultToValues(value: Any): List<String>? = null
