package io.ktor.webjars

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.pipeline.PipelineContext
import io.ktor.request.httpMethod
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.util.AttributeKey
import org.webjars.MultipleMatchesException
import org.webjars.WebJarAssetLocator
import java.nio.file.Paths

class Webjars(val configuration: Configuration) {

    private fun fileName(path: String) : String = Paths.get(path).fileName?.toString() ?: ""

    private fun extractWebJar(path : String) : String {
        val firstDelimiter = if (path.startsWith("/")) 1 else 0
        val nextDelimiter = path.indexOf("/", 1)
        val webjar = if(nextDelimiter > -1) path.substring(firstDelimiter,  nextDelimiter) else ""
        val partialPath = path.substring(nextDelimiter + 1)
        return locator.getFullPath(webjar, partialPath)
    }

    private val locator = WebJarAssetLocator()

    class Configuration {
        var path = "/webjars/"
        set(value) {
            var buffer = StringBuilder(value)
            if(!value.startsWith("/")){
                buffer.insert(0, "/")
            }
            if(!buffer.endsWith("/")){
                buffer.append("/")
            }
            field = buffer.toString()
        }
    }

    private suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>){
        val fullPath = context.call.request.uri
        val fileName = fileName(context.call.request.uri)
        if(fullPath.startsWith(configuration.path) && context.call.request.httpMethod == HttpMethod.Get && fileName.isNotEmpty()){
            val resourcePath = fullPath.removePrefix(configuration.path)
            try {
                val location = extractWebJar(resourcePath)
                context.call.respondBytes(ContentType.defaultForFilePath(fileName), HttpStatusCode.OK) {
                    Webjars::class.java.classLoader.getResourceAsStream(location).readBytes()
                }
            }catch (multipleFiles: MultipleMatchesException){
                context.call.respond(HttpStatusCode.InternalServerError)
            }
            catch (notFound: IllegalArgumentException){
            }
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Webjars.Configuration, Webjars>{

        override val key = AttributeKey<Webjars>("Webjars")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Webjars {
            val configuration = Configuration().apply(configure)

            val feature = Webjars(configuration)

            pipeline.intercept(ApplicationCallPipeline.Infrastructure){
                feature.intercept(this)
            }
            return feature
        }

    }

}