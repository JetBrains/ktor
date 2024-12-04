/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * A message either from the client or the server,
 * that has [headers] associated.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.HttpMessage)
 */
public interface HttpMessage {
    /**
     * Message [Headers]
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.HttpMessage.headers)
     */
    public val headers: Headers
}

/**
 * A builder message either for the client or the server,
 * that has a [headers] builder associated.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.HttpMessageBuilder)
 */
public interface HttpMessageBuilder {
    /**
     * MessageBuilder [HeadersBuilder]
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.HttpMessageBuilder.headers)
     */
    public val headers: HeadersBuilder
}
