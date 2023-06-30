/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet.jakarta

import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.security.*

/**
 * Returns Java's JAAS Principal
 */
public val Request.javaSecurityPrincipal: Principal?
    get() = when (this) {
        is ServletApplicationRequest -> servletRequest.userPrincipal
        is DelegateApplicationCall -> delegate.request.javaSecurityPrincipal
        else -> null
    }
