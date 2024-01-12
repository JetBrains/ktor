package io.ktor.server.webjars

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import org.webjars.*
import org.webjars.WebJarAssetLocator.*
import kotlin.time.*
import kotlin.time.Duration.Companion.days

/**
 * A configuration for the [Webjars] plugin.
 */
@KtorDsl
public class WebjarsConfig {
    private val installDate = GMTDate()
    internal var lastModifiedExtractor: (WebJarInfo) -> GMTDate? = { installDate }
    internal var etagExtractor: (WebJarInfo) -> String? = { it.version }
    internal var maxAgeExtractor: (WebJarInfo) -> Duration? = { 90.days }

    /**
     * Specifies a prefix for the path used to serve WebJars assets.
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

    /**
     * Specifies a value for [HttpHeaders.LastModified] to be used in the response.
     * By default, it is the time when this [Application] instance started.
     * Return `null` from this block to omit the header.
     *
     * Note: for this property to work, you need to install the [ConditionalHeaders] plugin.
     */
    public fun lastModified(block: (WebJarInfo) -> GMTDate?) {
        lastModifiedExtractor = block
    }

    /**
     * Specifies a value for [HttpHeaders.ETag] to be used in the response.
     * By default, it is the WebJar version.
     * Return `null` from this block to omit the header.
     *
     * Note: for this property to work, you need to install the [ConditionalHeaders] plugin.
     */
    public fun etag(block: (WebJarInfo) -> String?) {
        etagExtractor = block
    }

    /**
     * Specifies a value for [HttpHeaders.CacheControl] to be used in the response.
     * By default, it is 90 days.
     * Return `null` from this block to omit the header.
     *
     * Note: for this property to work, you need to install the [CachingHeaders] plugin.
     */
    public fun maxAge(block: (WebJarInfo) -> Duration?) {
        maxAgeExtractor = block
    }
}

/**
 * A plugin that enables serving the client-side libraries provided by WebJars.
 * It allows you to package your assets such as JavaScript and CSS libraries as part of your fat JAR.
 *
 * To learn more, see [Webjars](https://ktor.io/docs/webjars.html).
 */
public val Webjars: ApplicationPlugin<WebjarsConfig> = createApplicationPlugin("Webjars", ::WebjarsConfig) {
    val webjarsPrefix = pluginConfig.path
    require(webjarsPrefix.startsWith("/"))
    require(webjarsPrefix.endsWith("/"))
    val lastModifiedExtractor = pluginConfig.lastModifiedExtractor
    val etagExtractor = pluginConfig.etagExtractor
    val maxAgeExtractor = pluginConfig.maxAgeExtractor

    val locator = WebJarAssetLocator()
    val knownWebJars = locator.webJars.keys.toSet()

    onCall { call ->
        if (call.response.isCommitted) return@onCall

        val fullPath = call.request.path()
        if (!fullPath.startsWith(webjarsPrefix) ||
            call.request.httpMethod != HttpMethod.Get ||
            fullPath.last() == '/'
        ) {
            return@onCall
        }

        val resourcePath = fullPath.removePrefix(webjarsPrefix)
        try {
            call.attributes.put(StaticFileLocationProperty, resourcePath)
            val (location, info) = extractWebJar(resourcePath, knownWebJars, locator)
            val stream = WebjarsConfig::class.java.classLoader.getResourceAsStream(location) ?: return@onCall
            val content = InputStreamContent(stream, ContentType.defaultForFilePath(fullPath)).apply {
                lastModifiedExtractor(info)?.let { versions += LastModifiedVersion(it) }
                etagExtractor(info)?.let { versions += EntityTagVersion(it) }
                maxAgeExtractor(info)?.let { caching = CachingOptions(CacheControl.MaxAge(it.inWholeSeconds.toInt())) }
            }
            call.respond(content)
        } catch (multipleFiles: MultipleMatchesException) {
            call.respond(HttpStatusCode.InternalServerError)
        } catch (_: IllegalArgumentException) {
        }
    }
}
