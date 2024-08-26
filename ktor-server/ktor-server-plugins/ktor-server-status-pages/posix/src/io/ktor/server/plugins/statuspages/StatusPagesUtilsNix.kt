/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.statuspages

import kotlin.reflect.*

internal actual fun selectNearestParentClass(cause: Throwable, keys: List<KClass<*>>): KClass<*>? {
    if (keys.firstOrNull { cause::class == it } != null) return cause::class

    return keys.last()
}
