/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.errors

@Deprecated("Use kotlinx.io.IOException instead", ReplaceWith("kotlinx.io.IOException"))
public typealias IOException = kotlinx.io.IOException

@Deprecated("Use kotlinx.io.EOFException instead", ReplaceWith("kotlinx.io.EOFException"))
public typealias EOFException = kotlinx.io.EOFException

public expect open class UnknownServiceException(message: String) : kotlinx.io.IOException
