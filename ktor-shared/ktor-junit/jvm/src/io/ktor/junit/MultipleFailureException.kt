/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.junit

class MultipleFailureException(children: List<Throwable>) : RuntimeException(
    "Exceptions thrown: ${children.joinToString { it::class.simpleName ?: "<no name>" }}"
)
