/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @param path is a relative path to the resource
 * @param resourcePackage is a base package the path to be appended to
 * @param mimeResolve is a function that resolves content type by file extension, optional
 *
 * @return [LocalFileContent] or [JarFileContent] or `null`
 */
@OptIn(InternalAPI::class)
public fun ApplicationCall.resolveResource(
    path: String,
    resourcePackage: String? = null,
    classLoader: ClassLoader = application.environment.classLoader,
    mimeResolve: (String) -> ContentType = { ContentType.defaultForFileExtension(it) }
): OutgoingContent.ReadChannelContent? {
    if (path.endsWith("/") || path.endsWith("\\")) {
        return null
    }

    val normalizedPath = normalisedPath(resourcePackage, path)

    for (url in classLoader.getResources(normalizedPath).asSequence()) {
        resourceClasspathResource(url, normalizedPath) { mimeResolve(it.path.extension()) }?.let { content ->
            return content
        }
    }

    return null
}

private val resourceCache by lazy { ConcurrentHashMap<String, URL>() }

@OptIn(InternalAPI::class)
internal fun Application.resolveResource(
    path: String,
    resourcePackage: String? = null,
    classLoader: ClassLoader = environment.classLoader,
    mimeResolve: (URL) -> ContentType
): Pair<URL, OutgoingContent.ReadChannelContent>? {
    if (path.endsWith("/") || path.endsWith("\\")) {
        return null
    }

    val normalizedPath = normalisedPath(resourcePackage, path)
    val cacheKey = "${classLoader.hashCode()}/$normalizedPath"
    val resolveContent: (URL) -> Pair<URL, OutgoingContent.ReadChannelContent>? = { url ->
        resourceClasspathResource(url, normalizedPath, mimeResolve)?.let { url to it }
    }
    return resourceCache[cacheKey]?.let(resolveContent)
        ?: classLoader.getResources(normalizedPath).asSequence()
            .firstNotNullOfOrNull(resolveContent)?.also { (url) ->
                resourceCache[cacheKey] = url
            }
}

/**
 * Attempt to find a local file or a file inside of zip. This is not required but very good to have
 * to improve performance and unnecessary [java.io.InputStream] creation.
 */
@InternalAPI
public fun resourceClasspathResource(
    url: URL,
    path: String,
    mimeResolve: (URL) -> ContentType
): OutgoingContent.ReadChannelContent? {
    return when (url.protocol) {
        "file" -> {
            val file = File(url.path.decodeURLPart())
            if (file.isFile) LocalFileContent(file, mimeResolve(url)) else null
        }

        "jar" -> {
            if (path.endsWith("/")) {
                null
            } else {
                val zipFile = findContainingJarFile(url.toString())
                val content = JarFileContent(zipFile, path, mimeResolve(url))
                if (content.isFile) content else null
            }
        }

        "jrt", "resource" -> {
            URIFileContent(url, mimeResolve(url))
        }

        else -> null
    }
}

internal fun findContainingJarFile(url: String): File {
    if (url.startsWith("jar:file:")) {
        val jarPathSeparator = url.indexOf("!", startIndex = 9)
        require(jarPathSeparator != -1) { "Jar path requires !/ separator but it is: $url" }

        return File(url.substring(9, jarPathSeparator).decodeURLPart())
    }

    throw IllegalArgumentException("Only local jars are supported (jar:file:)")
}

internal fun String.extension(): String {
    val indexOfName = lastIndexOf('/').takeIf { it != -1 } ?: lastIndexOf('\\').takeIf { it != -1 } ?: 0
    val indexOfDot = indexOf('.', indexOfName)
    return if (indexOfDot >= 0) substring(indexOfDot) else ""
}

private fun normalisedPath(resourcePackage: String?, path: String): String {
    // note: we don't need to check for ".." in the normalizedPath because all ".." get replaced with //
    val pathComponents = path.split('/', '\\')
    if (pathComponents.contains("..")) {
        throw BadRequestException("Relative path should not contain path traversing characters: $path")
    }
    return (resourcePackage.orEmpty().split('.', '/', '\\') + pathComponents)
        .normalizePathComponents()
        .joinToString("/")
}
