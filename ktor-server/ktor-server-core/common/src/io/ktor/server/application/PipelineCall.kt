/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

private val RECEIVE_TYPE_KEY: AttributeKey<TypeInfo> = AttributeKey("ReceiveType")

/**
 * A single act of communication between a client and server.
 * @see [io.ktor.server.request.ApplicationRequest]
 * @see [io.ktor.server.response.ApplicationResponse]
 */
public interface ApplicationCall {
    /**
     * [Attributes] attached to this call.
     */
    public val attributes: Attributes

    /**
     * An [ApplicationRequest] that is a client request.
     */
    public val request: ApplicationRequest

    /**
     * An [PipelineResponse] that is a server response.
     */
    public val response: ApplicationResponse

    /**
     * An application being called.
     */
    public val application: Application

    /**
     * Parameters associated with this call.
     */
    public val parameters: Parameters

    /**
     * Receives content for this request.
     * @param typeInfo instance specifying type to be received.
     * @return instance of [T] received from this call.
     * @throws ContentTransformationException when content cannot be transformed to the requested type.
     */
    public suspend fun <T> receiveNullable(typeInfo: TypeInfo): T?

    /**
     * Sends a [message] as a response.
     * @see [io.ktor.server.response.PipelineResponse]
     */
    public suspend fun respond(message: Any?, typeInfo: TypeInfo?)
}

/**
 * A single act of communication between a client and server.
 * @see [io.ktor.server.request.PipelineRequest]
 * @see [io.ktor.server.response.PipelineResponse]
 */
public interface PipelineCall : ApplicationCall {

    /**
     * An [PipelineRequest] that is a client request.
     */
    public override val request: PipelineRequest

    /**
     * An [PipelineResponse] that is a server response.
     */
    public override val response: PipelineResponse

    public override suspend fun <T> receiveNullable(typeInfo: TypeInfo): T? {
        val token = attributes.getOrNull(DoubleReceivePreventionTokenKey)
        if (token == null) {
            attributes.put(DoubleReceivePreventionTokenKey, DoubleReceivePreventionToken)
        }

        receiveType = typeInfo
        val incomingContent = token ?: request.receiveChannel()
        val transformed = request.pipeline.execute(this, incomingContent)
        when {
            transformed == NullBody -> return null
            transformed === DoubleReceivePreventionToken -> throw RequestAlreadyConsumedException()
            !typeInfo.type.isInstance(transformed) -> throw CannotTransformContentToTypeException(typeInfo.kotlinType!!)
        }

        @Suppress("UNCHECKED_CAST")
        return transformed as T
    }

    @OptIn(InternalAPI::class)
    override suspend fun respond(message: Any?, typeInfo: TypeInfo?) {
        response.responseType = typeInfo
        response.pipeline.execute(this, message ?: NullBody)
    }
}

/**
 * Indicates if a response is sent.
 */
public val ApplicationCall.isHandled: Boolean get() = response.isCommitted

/**
 * The [TypeInfo] recorded from the last [call.receive<Type>()] call.
 */
public var ApplicationCall.receiveType: TypeInfo
    get() = attributes[RECEIVE_TYPE_KEY]
    internal set(value) {
        attributes.put(RECEIVE_TYPE_KEY, value)
    }
