/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

/**
 * Exception that is expected to be thrown during test execution. It will not be logged and will not fail the test.
 */
public open class ExpectedTestException(message: String) : Throwable(message)
