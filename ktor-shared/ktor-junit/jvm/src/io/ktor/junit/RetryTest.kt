/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.junit

import org.junit.jupiter.api.extension.*
import java.lang.reflect.*
import java.util.*

class RetryOnException : InvocationInterceptor {

    @Throws(Throwable::class)
    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext
    ) {
        try {
            invocation.proceed()
            return
        } catch (ex: Throwable) {
            // Failed, try again
            invocation.proceed()
        }
    }
}
