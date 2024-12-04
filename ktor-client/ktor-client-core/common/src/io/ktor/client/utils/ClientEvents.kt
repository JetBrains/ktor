/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import kotlin.native.concurrent.*

/**
 * Occurs after the creation of a new request
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.utils.HttpRequestCreated)
 */
public val HttpRequestCreated: EventDefinition<HttpRequestBuilder> = EventDefinition()

/**
 * Occurs before sending the request, and after execution of all interceptors.
 */
public val HttpRequestIsReadyForSending: EventDefinition<HttpRequestBuilder> = EventDefinition()

/**
 * Occurs after responses headers have been received.
 */
public val HttpResponseReceived: EventDefinition<HttpResponse> = EventDefinition()

/**
 * Utility class containing response and fail reasons for an [HttpResponseReceiveFailed] event.
 */
public class HttpResponseReceiveFail(public val response: HttpResponse, public val cause: Throwable)

/**
 * Occurs when an exception is thrown during receiving of body.
 */
public val HttpResponseReceiveFailed: EventDefinition<HttpResponseReceiveFail> = EventDefinition()

/**
 * Occurs when the response is cancelled due to an exception.
 */
public val HttpResponseCancelled: EventDefinition<HttpResponse> = EventDefinition()
