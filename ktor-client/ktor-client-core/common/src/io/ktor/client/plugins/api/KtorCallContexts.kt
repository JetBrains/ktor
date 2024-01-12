/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * A context for [ClientPluginBuilder.onRequest] callback.
 */
@KtorDsl
public class OnRequestContext internal constructor()

/**
 * A context for [ClientPluginBuilder.onResponse] callback.
 */
@KtorDsl
public class OnResponseContext internal constructor()

/**
 * A context for [ClientPluginBuilder.transformRequestBody] callback.
 */
@KtorDsl
public class TransformRequestBodyContext internal constructor()

/**
 * A context for [ClientPluginBuilder.transformResponseBody] callback.
 */
@KtorDsl
public class TransformResponseBodyContext internal constructor()

internal object RequestHook : ClientHook<suspend OnRequestContext.(request: HttpRequestBuilder, content: Any) -> Unit> {

    override fun install(
        client: HttpClient,
        handler: suspend OnRequestContext.(request: HttpRequestBuilder, content: Any) -> Unit
    ) {
        client.requestPipeline.intercept(HttpRequestPipeline.State) {
            handler(OnRequestContext(), context, subject)
        }
    }
}

internal object ResponseHook : ClientHook<suspend OnResponseContext.(response: HttpResponse) -> Unit> {

    override fun install(
        client: HttpClient,
        handler: suspend OnResponseContext.(response: HttpResponse) -> Unit
    ) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) {
            handler(OnResponseContext(), subject)
        }
    }
}

internal object TransformRequestBodyHook : ClientHook<
    suspend TransformRequestBodyContext.(
        request: HttpRequestBuilder,
        content: Any,
        bodyType: TypeInfo?
    ) -> OutgoingContent?
    > {

    override fun install(
        client: HttpClient,
        handler: suspend TransformRequestBodyContext.(
            request: HttpRequestBuilder,
            content: Any,
            bodyType: TypeInfo?
        ) -> OutgoingContent?
    ) {
        client.requestPipeline.intercept(HttpRequestPipeline.Transform) {
            val newContent = handler(TransformRequestBodyContext(), context, subject, context.bodyType)
            if (newContent != null) proceedWith(newContent)
        }
    }
}

internal object TransformResponseBodyHook :
    ClientHook<
        suspend TransformResponseBodyContext.(
            response: HttpResponse,
            content: ByteReadChannel,
            requestedType: TypeInfo
        ) -> Any?
        > {

    override fun install(
        client: HttpClient,
        handler: suspend TransformResponseBodyContext.(
            response: HttpResponse,
            content: ByteReadChannel,
            requestedType: TypeInfo
        ) -> Any?
    ) {
        client.responsePipeline.intercept(HttpResponsePipeline.Transform) {
            val (typeInfo, content) = subject
            if (content !is ByteReadChannel) return@intercept
            val newContent = handler(TransformResponseBodyContext(), context.response, content, typeInfo)
                ?: return@intercept
            if (newContent !is NullBody && !typeInfo.type.isInstance(newContent)) {
                throw IllegalStateException(
                    "transformResponseBody returned $newContent but expected value of type $typeInfo"
                )
            }
            proceedWith(HttpResponseContainer(typeInfo, newContent))
        }
    }
}
