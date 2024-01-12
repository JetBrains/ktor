/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

/**
 * Represents a cause for an authentication challenge request.
 */
public sealed class AuthenticationFailedCause {
    /**
     * Represents a case when no credentials are provided.
     */
    public object NoCredentials : AuthenticationFailedCause()

    /**
     * Represents a case when invalid credentials are provided.
     */
    public object InvalidCredentials : AuthenticationFailedCause()

    /**
     * Represents a case when authentication mechanism failed.
     * @param message describing the cause of the authentication failure.
     */
    public open class Error(public val message: String) : AuthenticationFailedCause()
}
