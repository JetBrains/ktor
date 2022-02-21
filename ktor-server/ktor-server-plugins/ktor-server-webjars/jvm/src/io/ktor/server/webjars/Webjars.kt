package io.ktor.server.webjars

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*
import org.webjars.*

/**
 * Webjars plugin configuration.
 */
@KtorDsl
public class WebjarsConfig {
    /**
     * Path prefix at which the installed plugin responds.
     */
    public var path: String = "/webjars/"
        set(value) {
            field = buildString(value.length + 2) {
                if (!value.startsWith('/')) {
                    append('/')
                }
                append(value)
                if (!endsWith('/')) {
                    append('/')
                }
            }
        }
}

/**
 * This plugin listens to requests starting with the specified path prefix and responding with static content
 * packaged into webjars. A [WebJarAssetLocator] is used to look for static files.
 */
public val Webjars: ApplicationPlugin<WebjarsConfig> = createApplicationPlugin("Webjars", ::WebjarsConfig) {
    val webjarsPrefix = pluginConfig.path
    require(webjarsPrefix.startsWith("/"))
    require(webjarsPrefix.endsWith("/"))

    val locator = WebJarAssetLocator()
    val knownWebJars = locator.webJars?.keys?.toSet() ?: emptySet()
    val lastModified = GMTDate()

    onCall { call ->
        if (call.response.isCommitted) return@onCall

        val fullPath = call.request.path()
        if (fullPath.startsWith(webjarsPrefix) && call.request.httpMethod == HttpMethod.Get && fullPath.last() != '/') {
            val resourcePath = fullPath.removePrefix(webjarsPrefix)
            try {
                val location = extractWebJar(resourcePath, knownWebJars, locator)
                val stream = WebjarsConfig::class.java.classLoader.getResourceAsStream(location) ?: return@onCall
                val content = InputStreamContent(stream, ContentType.defaultForFilePath(fullPath), lastModified)
                call.respond(content)
            } catch (multipleFiles: MultipleMatchesException) {
                call.respond(HttpStatusCode.InternalServerError)
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
