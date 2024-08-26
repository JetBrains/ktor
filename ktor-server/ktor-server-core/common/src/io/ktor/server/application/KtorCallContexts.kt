/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.server.application

import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * The context associated with the call that is currently being processed by server.
 * Every call handler ([PluginBuilder.onCall], [PluginBuilder.onCallReceive], [PluginBuilder.onCallRespond], and so on)
 * of your plugin has a derivative of [CallContext] as a receiver.
 **/
@KtorDsl
public open class CallContext<PluginConfig : Any> internal constructor(
    public val pluginConfig: PluginConfig,
    protected open val context: PipelineContext<*, PipelineCall>
) {
    // Internal usage for tests only
    internal fun finish() = context.finish()
}

/**
 * A context associated with the call handling by your application. [OnCallContext] is a receiver for [PluginBuilder.onCall] handler
 * of your [PluginBuilder].
 *
 * @see CallContext
 **/
@KtorDsl
public class OnCallContext<PluginConfig : Any> internal constructor(
    pluginConfig: PluginConfig,
    context: PipelineContext<Unit, PipelineCall>
) : CallContext<PluginConfig>(pluginConfig, context)

/**
 * Contains type information about the current request or response body when performing a transformation.
 * */
@KtorDsl
public class TransformBodyContext(public val requestedType: TypeInfo?)

/**
 * A context associated with the call.receive() action. Allows you to transform the received body.
 * [OnCallReceiveContext] is a receiver for [PluginBuilder.onCallReceive] handler of your [PluginBuilder].
 *
 * @see CallContext
 **/
@KtorDsl
public class OnCallReceiveContext<PluginConfig : Any> internal constructor(
    pluginConfig: PluginConfig,
    override val context: PipelineContext<Any, PipelineCall>
) : CallContext<PluginConfig>(pluginConfig, context) {
    /**
     * Specifies how to transform a request body that is being received from a client.
     * If another plugin has already made the transformation, then your [transformBody] handler is not executed.
     **/
    public suspend fun transformBody(transform: suspend TransformBodyContext.(body: ByteReadChannel) -> Any) {
        val receiveBody = context.subject as? ByteReadChannel ?: return
        val typeInfo = context.call.receiveType
        if (typeInfo == typeInfo<ByteReadChannel>()) return

        val transformContext = TransformBodyContext(typeInfo)
        context.subject = transformContext.transform(receiveBody)
    }
}

/**
 *  A context associated with the call.respond() action. Allows you to transform the response body.
 *  [OnCallRespondContext] is a receiver for [PluginBuilder.onCallRespond] handler of your [PluginBuilder].
 *
 * @see CallContext
 **/
@KtorDsl
public class OnCallRespondContext<PluginConfig : Any> internal constructor(
    pluginConfig: PluginConfig,
    override val context: PipelineContext<Any, PipelineCall>
) : CallContext<PluginConfig>(pluginConfig, context) {
    /**
     * Specifies how to transform a response body that is being sent to a client.
     **/
    public suspend fun transformBody(transform: suspend TransformBodyContext.(body: Any) -> Any) {
        val transformContext = TransformBodyContext(context.call.response.responseType)

        context.subject = transformContext.transform(context.subject)
    }
}
