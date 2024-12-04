/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.parsing

/**
 * Thrown to indicate that the application has attempted to convert
 * a string to one of the specific types, but that the string does not
 * have the appropriate format.
 * Please check the message for more details on the failure.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.parsing.ParseException)
 * */
public class ParseException(
    override val message: String,
    override val cause: Throwable? = null
) : IllegalArgumentException(message, cause)
