/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.velocity

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import org.apache.velocity.*
import org.apache.velocity.app.*
import org.apache.velocity.context.*

/**
 * Represents a response content that could be used to respond with `call.respond(VelocityContent(...))`
 *
 * @param template name to be resolved by velocity
 * @param model to be passed to the template
 * @param etag header value (optional)
 * @param contentType (optional, `text/html` with UTF-8 character encoding by default)
 */
public class VelocityContent(
    public val template: String,
    public val model: Map<String, Any>,
    public val etag: String? = null,
    public val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

internal class VelocityOutgoingContent(
    val template: Template,
    val model: Context,
    etag: String?,
    override val contentType: ContentType
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.bufferedWriter(contentType.charset() ?: Charsets.UTF_8).use {
            template.merge(model, it)
        }
    }

    init {
        if (etag != null) {
            versions += EntityTagVersion(etag)
        }
    }
}

/**
 * Velocity ktor feature. Provides ability to respond with [VelocityContent] and [respondTemplate].
 */
public class Velocity(private val engine: VelocityEngine) {
    init {
        engine.init()
    }

    /**
     * A companion object for installing feature
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, VelocityEngine, Velocity> {
        override val key: AttributeKey<Velocity> = AttributeKey<Velocity>("velocity")

        override fun install(pipeline: ApplicationCallPipeline, configure: VelocityEngine.() -> Unit): Velocity {
            val config = VelocityEngine().apply(configure)
            val feature = Velocity(config)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is VelocityContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }
            return feature
        }
    }

    internal fun process(content: VelocityContent): VelocityOutgoingContent {
        return VelocityOutgoingContent(
            engine.getTemplate(content.template),
            VelocityContext(content.model),
            content.etag,
            content.contentType
        )
    }
}
