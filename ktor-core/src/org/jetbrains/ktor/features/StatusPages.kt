package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.*

class StatusPages(config: Configuration) {
    val exceptions = HashMap(config.exceptions)
    val statuses = HashMap(config.statuses)

    class Configuration {
        val exceptions = mutableMapOf<Class<*>, suspend PipelineContext<ApplicationCall>.(Throwable) -> Unit>()
        val statuses = mutableMapOf<HttpStatusCode, suspend PipelineContext<ApplicationCall>.(HttpStatusCode) -> Unit>()

        inline fun <reified T : Any> exception(noinline handler: suspend PipelineContext<ApplicationCall>.(Throwable) -> Unit) =
                exception(T::class.java, handler)

        fun exception(klass: Class<*>, handler: suspend PipelineContext<ApplicationCall>.(Throwable) -> Unit) {
            exceptions.put(klass, handler)
        }

        fun status(vararg status: HttpStatusCode, handler: suspend PipelineContext<ApplicationCall>.(HttpStatusCode) -> Unit) {
            status.forEach {
                statuses.put(it, handler)
            }
        }
    }

    suspend private fun intercept(context: PipelineContext<ApplicationCall>) {
        var statusHandled = false
        context.call.response.pipeline.intercept(ApplicationResponsePipeline.After) {
            if (!statusHandled) {
                val message = subject
                val status = when (message) {
                    is FinalContent -> message.status
                    is HttpStatusCode -> message
                    else -> null
                }
                val handler = statuses[status]
                if (handler != null) {
                    statusHandled = true
                    context.handler(status!!)
                }
            }
        }

        try {
            context.proceed()
        } catch(exception: Throwable) {
            if (context.call.response.status() == null) {
                val handler = findHandlerByType(exception.javaClass)
                if (handler != null) {
                    context.handler(exception)
                } else
                    throw exception
            }
        }
    }

    private fun findHandlerByType(clazz: Class<*>): (suspend PipelineContext<ApplicationCall>.(Throwable) -> Unit)? {
        exceptions[clazz]?.let { return it }
        clazz.superclass?.let {
            findHandlerByType(it)?.let { return it }
        }
        clazz.interfaces.forEach {
            findHandlerByType(it)?.let { return it }
        }
        return null
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, StatusPages> {
        override val key = AttributeKey<StatusPages>("Status Pages")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): StatusPages {
            val configuration = Configuration().apply(configure)
            val feature = StatusPages(configuration)
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(this) }
            return feature
        }
    }
}

fun StatusPages.Configuration.statusFile(vararg status: HttpStatusCode, filePattern: String, contentType: ContentType = ContentType.Text.Html) {
    status(*status) { status ->
        val path = filePattern.replace("#", status.value.toString())
        val message = call.resolveClasspathWithPath("", path)
        if (message == null) {
            call.respond(HttpStatusCode.InternalServerError)
        } else {
            call.respond(message)
        }
    }
}
