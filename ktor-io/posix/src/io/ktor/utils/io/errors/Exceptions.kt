/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.errors

public actual open class UnknownServiceException actual constructor(message: String) :
    kotlinx.io.IOException()
