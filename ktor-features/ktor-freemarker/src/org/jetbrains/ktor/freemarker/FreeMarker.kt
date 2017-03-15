package org.jetbrains.ktor.freemarker

import freemarker.template.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.content.Version
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*

class FreeMarkerContent(val templateName: String,
                        val model: Any,
                        val etag: String,
                        val contentType: ContentType = ContentType.Text.Html)

class FreeMarker(val config: Configuration) {
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, FreeMarker> {
        override val key = AttributeKey<FreeMarker>("freemarker")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): FreeMarker {
            val config = Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS).apply(configure)
            val feature = FreeMarker(config)
            pipeline.feature(ApplicationTransform).register<FreeMarkerContent> { model -> feature.process(model) }
            return feature
        }
    }

    private fun process(content: FreeMarkerContent): FreeMarkerTemplateResource {
        return FreeMarkerTemplateResource(config.getTemplate(content.templateName), content.model, content.etag, content.contentType)
    }

    private class FreeMarkerTemplateResource(val template: freemarker.template.Template,
                                             val model: Any,
                                             val etag: String,
                                             override val contentType: ContentType) : FinalContent.WriteChannelContent(), Resource {
        suspend override fun writeTo(channel: WriteChannel) {
            with(channel.toOutputStream().bufferedWriter(contentType.charset() ?: Charsets.UTF_8)) {
                template.process(model, this)
            }
        }

        override val versions: List<Version>
            get() = listOf(EntityTagVersion(etag))

        override val expires = null
        override val cacheControl = null
        override val contentLength = null

        override val headers by lazy { super<Resource>.headers }
    }
}
