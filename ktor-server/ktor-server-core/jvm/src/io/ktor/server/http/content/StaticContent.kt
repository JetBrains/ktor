/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.io.*
import java.net.*
import java.nio.file.*
import kotlin.io.path.*

/**
 * Attribute to assign the path of a static file served in the response.  The main use of this attribute is to indicate
 * to subsequent interceptors that a static file was served via the `ApplicationCall.isStaticContent()` extension
 * function.
 */
public val StaticFileLocationProperty: AttributeKey<String> = AttributeKey("StaticFileLocation")

private const val pathParameterName = "static-content-path-parameter"

private val staticRootFolderKey = AttributeKey<File>("BaseFolder")

private val StaticContentAutoHead = createRouteScopedPlugin("StaticContentAutoHead") {

    class HeadResponse(val original: OutgoingContent) : OutgoingContent.NoContent() {
        override val status: HttpStatusCode? get() = original.status
        override val contentType: ContentType? get() = original.contentType
        override val contentLength: Long? get() = original.contentLength
        override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
        override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
        override val headers get() = original.headers
    }

    on(ResponseBodyReadyForSend) { call, content ->
        check(call.request.local.method == HttpMethod.Head)
        if (content is OutgoingContent.ReadChannelContent) content.readFrom().cancel(null)
        transformBodyTo(HeadResponse(content))
    }
}

/**
 * A config for serving static content
 */
public class StaticContentConfig<Resource : Any> internal constructor() {

    private val defaultContentType: (Resource) -> ContentType = {
        when (it) {
            is File -> ContentType.defaultForFile(it)
            is URL -> ContentType.defaultForFilePath(it.path)
            is Path -> ContentType.defaultForPath(it)
            else ->
                throw IllegalArgumentException("Argument can be only of type File, Path or URL, but was ${it::class}")
        }
    }
    internal var contentType: (Resource) -> ContentType = defaultContentType
    internal var cacheControl: (Resource) -> List<CacheControl> = { emptyList() }
    internal var modifier: suspend (Resource, ApplicationCall) -> Unit = { _, _ -> }
    internal var exclude: (Resource) -> Boolean = { false }
    internal var extensions: List<String> = emptyList()
    internal var defaultPath: String? = null
    internal var preCompressedFileTypes: List<CompressedFileType> = emptyList()
    internal var autoHeadResponse: Boolean = false

    /**
     * Enables pre-compressed files or resources.
     *
     * For example, for static files, by setting `preCompressed(CompressedFileType.BROTLI)`, the local file
     * /foo/bar.js.br can be found at "/foo/bar.js"
     *
     * Appropriate headers will be set and compression will be suppressed if pre-compressed file is found.
     *
     * The order in types is *important*.
     * It will determine the priority of serving one versus serving another.
     */
    public fun preCompressed(vararg types: CompressedFileType) {
        preCompressedFileTypes = types.toList()
    }

    /**
     * Enables automatic response to a `HEAD` request for every file/resource that has a `GET` defined.
     */
    public fun enableAutoHeadResponse() {
        autoHeadResponse = true
    }

    /**
     * Configures default [Resource] to respond with, when requested file is not found.
     */
    public fun default(path: String?) {
        this.defaultPath = path
    }

    /**
     * Configures [ContentType] for requested static content.
     * If the [block] returns `null`, default behaviour of guessing [ContentType] from the header will be used.
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     */
    public fun contentType(block: (Resource) -> ContentType?) {
        contentType = { resource -> block(resource) ?: defaultContentType(resource) }
    }

    /**
     * Configures [CacheControl] for requested static content.
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     */
    public fun cacheControl(block: (Resource) -> List<CacheControl>) {
        cacheControl = block
    }

    /**
     * Configures modification of a call for requested content.
     * Useful to add headers to the response, such as [HttpHeaders.ETag]
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     */
    public fun modify(block: suspend (Resource, ApplicationCall) -> Unit) {
        modifier = block
    }

    /**
     * Configures resources that should not be served.
     * If this block returns `true` for [Resource], [Application] will respond with [HttpStatusCode.Forbidden].
     * Can be invoked multiple times.
     * For files, [Resource] is a requested [File].
     * For resources, [Resource] is a [URL] to a requested resource.
     */
    public fun exclude(block: (Resource) -> Boolean) {
        val oldBlock = exclude
        exclude = {
            if (oldBlock(it)) {
                true
            } else {
                block(it)
            }
        }
    }

    /**
     * Configures file extension fallbacks.
     * When set, if a file is not found, the search will repeat with the given extensions added to the file name.
     * The first match will be served.
     */
    public fun extensions(vararg extensions: String) {
        this.extensions = extensions.toList()
    }
}

/**
 * Sets up [Routing] to serve static files.
 * All files inside [dir] will be accessible recursively at "[remotePath]/path/to/file".
 * If the requested file is a directory and [index] is not `null`,
 * then response will be [index] file in the requested directory.
 *
 * If the requested file doesn't exist, or it is a directory and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 */
public fun Route.staticFiles(
    remotePath: String,
    dir: File,
    index: String? = "index.html",
    block: StaticContentConfig<File>.() -> Unit = {}
): Route {
    val staticRoute = StaticContentConfig<File>().apply(block)
    val autoHead = staticRoute.autoHeadResponse
    val compressedTypes = staticRoute.preCompressedFileTypes
    val contentType = staticRoute.contentType
    val cacheControl = staticRoute.cacheControl
    val extensions = staticRoute.extensions
    val modify = staticRoute.modifier
    val exclude = staticRoute.exclude
    val defaultPath = staticRoute.defaultPath
    return staticContentRoute(remotePath, autoHead) {
        respondStaticFile(
            index = index,
            dir = dir,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            modify = modify,
            exclude = exclude,
            extensions = extensions,
            defaultPath = defaultPath
        )
    }
}

/**
 * Sets up [Routing] to serve resources as static content.
 * All resources inside [basePackage] will be accessible recursively at "[remotePath]/path/to/resource".
 * If requested resource doesn't exist and [index] is not `null`,
 * then response will be [index] resource in the requested package.
 *
 * If requested resource doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 */
public fun Route.staticResources(
    remotePath: String,
    basePackage: String?,
    index: String? = "index.html",
    block: StaticContentConfig<URL>.() -> Unit = {}
): Route {
    val staticRoute = StaticContentConfig<URL>().apply(block)
    val autoHead = staticRoute.autoHeadResponse
    val compressedTypes = staticRoute.preCompressedFileTypes
    val contentType = staticRoute.contentType
    val cacheControl = staticRoute.cacheControl
    val extensions = staticRoute.extensions
    val modifier = staticRoute.modifier
    val exclude = staticRoute.exclude
    val defaultPath = staticRoute.defaultPath
    return staticContentRoute(remotePath, autoHead) {
        respondStaticResource(
            index = index,
            basePackage = basePackage,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            modifier = modifier,
            exclude = exclude,
            extensions = extensions,
            defaultPath = defaultPath
        )
    }
}

/**
 * Sets up [Routing] to serve contents of [zip] as static content.
 * All paths inside [basePath] will be accessible recursively at "[remotePath]/path/to/resource".
 * If requested path doesn't exist and [index] is not `null`,
 * then response will be [index] path in the requested package.
 *
 * If requested path doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 */
public fun Route.staticZip(
    remotePath: String,
    basePath: String?,
    zip: Path,
    index: String? = "index.html",
    block: StaticContentConfig<Path>.() -> Unit = {}
): Route = staticFileSystem(
    remotePath = remotePath,
    basePath = basePath,
    index = index,
    fileSystem = FileSystems.newFileSystem(zip, environment.classLoader),
    block = block
)

/**
 * Sets up [Routing] to serve [fileSystem] as static content.
 * All paths inside [basePath] will be accessible recursively at "[remotePath]/path/to/resource".
 * If requested path doesn't exist and [index] is not `null`,
 * then response will be [index] path in the requested package.
 *
 * If requested path doesn't exist and no [index] specified, response will be 404 Not Found.
 *
 * You can use [block] for additional set up.
 */
public fun Route.staticFileSystem(
    remotePath: String,
    basePath: String?,
    index: String? = "index.html",
    fileSystem: FileSystem = FileSystems.getDefault(),
    block: StaticContentConfig<Path>.() -> Unit = {}
): Route {
    val staticRoute = StaticContentConfig<Path>().apply(block)
    val autoHead = staticRoute.autoHeadResponse
    val compressedTypes = staticRoute.preCompressedFileTypes
    val contentType = staticRoute.contentType
    val cacheControl = staticRoute.cacheControl
    val extensions = staticRoute.extensions
    val modify = staticRoute.modifier
    val exclude = staticRoute.exclude
    val defaultPath = staticRoute.defaultPath
    return staticContentRoute(remotePath, autoHead) {
        respondStaticPath(
            fileSystem = fileSystem,
            index = index,
            basePath = basePath,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            modify = modify,
            exclude = exclude,
            extensions = extensions,
            defaultPath = defaultPath
        )
    }
}

/**
 * Support pre-compressed files and resources
 *
 * For example, by using [preCompressed()] (or [preCompressed(CompressedFileType.BROTLI)]), the local file
 * /foo/bar.js.br can be found @ http..../foo/bar.js
 *
 * Appropriate headers will be set and compression will be suppressed if pre-compressed file is found
 *
 * Notes:
 *
 * * The order in types is *important*. It will determine the priority of serving one versus serving another
 *
 * * This can't be disabled in a child route if it was enabled in the root route
 */
public fun Route.preCompressed(
    vararg types: CompressedFileType = CompressedFileType.values(),
    configure: Route.() -> Unit
) {
    val existing = staticContentEncodedTypes ?: emptyList()
    val mixedTypes = (existing + types.asList()).distinct()
    attributes.put(compressedKey, mixedTypes)
    configure()
    attributes.remove(compressedKey)
}

/**
 * Base folder for relative files calculations for static content
 */
public var Route.staticRootFolder: File?
    get() = attributes.getOrNull(staticRootFolderKey) ?: parent?.staticRootFolder
    set(value) {
        if (value != null) {
            attributes.put(staticRootFolderKey, value)
        } else {
            attributes.remove(staticRootFolderKey)
        }
    }

private fun File?.combine(file: File) = when {
    this == null -> file
    else -> resolve(file)
}

/**
 * Create a block for static content
 */
@Deprecated("Please use `staticFiles` or `staticResources` instead")
public fun Route.static(configure: Route.() -> Unit): Route = apply(configure)

/**
 * Create a block for static content at specified [remotePath]
 */
@Deprecated("Please use `staticFiles` or `staticResources` instead")
public fun Route.static(remotePath: String, configure: Route.() -> Unit): Route =
    route(remotePath, configure)

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 */
@Suppress("DEPRECATION")
@Deprecated("Please use `staticFiles` instead")
public fun Route.default(localPath: String): Unit = default(File(localPath))

/**
 * Specifies [localPath] as a default file to serve when folder is requested
 */
@Deprecated("Please use `staticFiles` instead")
public fun Route.default(localPath: File) {
    val file = staticRootFolder.combine(localPath)
    val compressedTypes = staticContentEncodedTypes
    get {
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 */
@Suppress("DEPRECATION")
@Deprecated("Please use `staticFiles` instead")
public fun Route.file(remotePath: String, localPath: String = remotePath): Unit =
    file(remotePath, File(localPath))

/**
 * Sets up routing to serve [localPath] file as [remotePath]
 */
@Deprecated("Please use `staticFiles` instead")
public fun Route.file(remotePath: String, localPath: File) {
    val file = staticRootFolder.combine(localPath)
    val compressedTypes = staticContentEncodedTypes
    get(remotePath) {
        call.respondStaticFile(file, compressedTypes)
    }
}

/**
 * Sets up routing to serve all files from [folder]
 */
@Suppress("DEPRECATION")
@Deprecated("Please use `staticFiles` instead")
public fun Route.files(folder: String): Unit = files(File(folder))

/**
 * Sets up routing to serve all files from [folder]
 */
@Deprecated("Please use `staticFiles` instead")
public fun Route.files(folder: File) {
    val dir = staticRootFolder.combine(folder)
    val compressedTypes = staticContentEncodedTypes
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val file = dir.combineSafe(relativePath)
        call.respondStaticFile(file, compressedTypes)
    }
}

private val staticBasePackageName = AttributeKey<String>("BasePackage")

/**
 * Base package for relative resources calculations for static content
 */
@Suppress("DEPRECATION")
@Deprecated("Please use `staticResources` instead")
public var Route.staticBasePackage: String?
    get() = attributes.getOrNull(staticBasePackageName) ?: parent?.staticBasePackage
    set(value) {
        if (value != null) {
            attributes.put(staticBasePackageName, value)
        } else {
            attributes.remove(staticBasePackageName)
        }
    }

private fun String?.combinePackage(resourcePackage: String?) = when {
    this == null -> resourcePackage
    resourcePackage == null -> this
    else -> "$this.$resourcePackage"
}

/**
 * Sets up routing to serve [resource] as [remotePath] in [resourcePackage]
 */
@Suppress("DEPRECATION")
@Deprecated("Please use `staticResources` instead")
public fun Route.resource(remotePath: String, resource: String = remotePath, resourcePackage: String? = null) {
    val compressedTypes = staticContentEncodedTypes
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get(remotePath) {
        call.respondStaticResource(
            requestedResource = resource,
            packageName = packageName,
            compressedTypes = compressedTypes
        )
    }
}

/**
 * Sets up routing to serve all resources in [resourcePackage]
 */
@Suppress("DEPRECATION")
@Deprecated("Please use `staticResources` instead")
public fun Route.resources(resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    val compressedTypes = staticContentEncodedTypes
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        call.respondStaticResource(
            requestedResource = relativePath,
            packageName = packageName,
            compressedTypes = compressedTypes
        )
    }
}

/**
 * Specifies [resource] as a default resources to serve when folder is requested
 */
@Suppress("DEPRECATION")
@Deprecated("Please use `staticResources` instead")
public fun Route.defaultResource(resource: String, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    val compressedTypes = staticContentEncodedTypes
    get {
        call.respondStaticResource(
            requestedResource = resource,
            packageName = packageName,
            compressedTypes = compressedTypes
        )
    }
}

/**
 *  Checks if the application call is requesting static content
 */
public fun ApplicationCall.isStaticContent(): Boolean = attributes.contains(StaticFileLocationProperty)

private fun Route.staticContentRoute(
    remotePath: String,
    autoHead: Boolean,
    handler: suspend (ApplicationCall).() -> Unit
) = route(remotePath) {
    route("{$pathParameterName...}") {
        get {
            call.handler()
        }
        if (autoHead) {
            method(HttpMethod.Head) {
                install(StaticContentAutoHead)
                handle {
                    call.handler()
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondStaticFile(
    index: String?,
    dir: File,
    compressedTypes: List<CompressedFileType>?,
    contentType: (File) -> ContentType,
    cacheControl: (File) -> List<CacheControl>,
    modify: suspend (File, ApplicationCall) -> Unit,
    exclude: (File) -> Boolean,
    extensions: List<String>,
    defaultPath: String?
) {
    val relativePath = parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return
    val requestedFile = dir.combineSafe(relativePath)

    suspend fun checkExclude(file: File): Boolean {
        if (!exclude(file)) return false
        respond(HttpStatusCode.Forbidden)
        return true
    }

    val isDirectory = requestedFile.isDirectory
    if (index != null && isDirectory) {
        respondStaticFile(File(requestedFile, index), compressedTypes, contentType, cacheControl, modify)
    } else if (!isDirectory) {
        if (checkExclude(requestedFile)) return

        respondStaticFile(requestedFile, compressedTypes, contentType, cacheControl, modify)
        if (isHandled) return
        for (extension in extensions) {
            val fileWithExtension = File("${requestedFile.path}.$extension")
            if (checkExclude(fileWithExtension)) return
            respondStaticFile(fileWithExtension, compressedTypes, contentType, cacheControl, modify)
            if (isHandled) return
        }
    }

    if (isHandled) return
    if (defaultPath != null) {
        respondStaticFile(File(dir, defaultPath), compressedTypes, contentType, cacheControl, modify)
    }
}

private suspend fun ApplicationCall.respondStaticPath(
    fileSystem: FileSystem,
    index: String?,
    basePath: String?,
    compressedTypes: List<CompressedFileType>?,
    contentType: (Path) -> ContentType,
    cacheControl: (Path) -> List<CacheControl>,
    modify: suspend (Path, ApplicationCall) -> Unit,
    exclude: (Path) -> Boolean,
    extensions: List<String>,
    defaultPath: String?
) {
    val relativePath = parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return
    val requestedPath = fileSystem.getPath(basePath ?: "").combineSafe(fileSystem.getPath(relativePath))

    suspend fun checkExclude(path: Path): Boolean {
        if (!exclude(path)) return false
        respond(HttpStatusCode.Forbidden)
        return true
    }

    val isDirectory = requestedPath.isDirectory()
    if (index != null && isDirectory) {
        respondStaticPath(fileSystem, requestedPath.resolve(index), compressedTypes, contentType, cacheControl, modify)
    } else if (!isDirectory) {
        if (checkExclude(requestedPath)) return

        respondStaticPath(fileSystem, requestedPath, compressedTypes, contentType, cacheControl, modify)
        if (isHandled) return
        for (extension in extensions) {
            val pathWithExtension = fileSystem.getPath("${requestedPath.pathString}.$extension")
            if (checkExclude(pathWithExtension)) return
            respondStaticPath(fileSystem, pathWithExtension, compressedTypes, contentType, cacheControl, modify)
            if (isHandled) return
        }
    }

    if (isHandled) return
    if (defaultPath != null) {
        respondStaticPath(
            fileSystem,
            fileSystem.getPath(basePath ?: "", defaultPath),
            compressedTypes,
            contentType,
            cacheControl,
            modify
        )
    }
}

private suspend fun ApplicationCall.respondStaticResource(
    index: String?,
    basePackage: String?,
    compressedTypes: List<CompressedFileType>?,
    contentType: (URL) -> ContentType,
    cacheControl: (URL) -> List<CacheControl>,
    modifier: suspend (URL, ApplicationCall) -> Unit,
    exclude: (URL) -> Boolean,
    extensions: List<String>,
    defaultPath: String?
) {
    val relativePath = parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return

    respondStaticResource(
        requestedResource = relativePath,
        packageName = basePackage,
        compressedTypes = compressedTypes,
        contentType = contentType,
        cacheControl = cacheControl,
        modifier = modifier,
        exclude = exclude
    )

    if (isHandled) return
    for (extension in extensions) {
        respondStaticResource(
            requestedResource = "$relativePath.$extension",
            packageName = basePackage,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            modifier = modifier,
            exclude = exclude
        )
        if (isHandled) return
    }

    if (index != null) {
        respondStaticResource(
            requestedResource = "$relativePath${File.separator}$index",
            packageName = basePackage,
            compressedTypes = compressedTypes,
            contentType = contentType,
            cacheControl = cacheControl,
            modifier = modifier
        )
    }
    if (isHandled || defaultPath == null) return

    respondStaticResource(
        requestedResource = defaultPath,
        packageName = basePackage,
        compressedTypes = compressedTypes,
        contentType = contentType,
        cacheControl = cacheControl,
        modifier = modifier
    )
}
