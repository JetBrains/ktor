package io.ktor.content

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import java.io.*

private val pathParameterName = "static-content-path-parameter"

val StaticRootFolderKey = AttributeKey<File>("BaseFolder")
var Route.staticRootFolder: File?
    get() = attributes.getOrNull(StaticRootFolderKey) ?: parent?.staticRootFolder
    set(value) {
        value?.let { attributes.put(StaticRootFolderKey, it) }
    }

private fun File?.combine(file: File) = when {
    this == null -> file
    else -> resolve(file)
}

fun Route.static(configure: Route.() -> Unit): Route {
    // need to create new Route to isolate its attributes
    val route = Route(this, UnconditionalRouteSelector).apply(configure)
    children.add(route)
    return route
}

fun Route.static(remotePath: String, configure: Route.() -> Unit) = route(remotePath, configure)

fun Route.default(localPath: String) = default(File(localPath))
fun Route.default(localPath: File) {
    val file = staticRootFolder.combine(localPath)
    get {
        if (file.isFile) {
            call.respond(LocalFileContent(file))
        }
    }
}

fun Route.file(remotePath: String, localPath: String = remotePath) = file(remotePath, File(localPath))
fun Route.file(remotePath: String, localPath: File) {
    val file = staticRootFolder.combine(localPath)
    get(remotePath) {
        if (file.isFile) {
            call.respond(LocalFileContent(file))
        }
    }
}

fun Route.files(folder: String) = files(File(folder))
fun Route.files(folder: File) {
    val dir = staticRootFolder.combine(folder)
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val file = dir.combineSafe(relativePath)
        if (file.isFile) {
            call.respond(LocalFileContent(file))
        }
    }
}

val BasePackageKey = AttributeKey<String>("BasePackage")
var Route.staticBasePackage: String?
    get() = attributes.getOrNull(BasePackageKey)
    set(value) {
        value?.let { attributes.put(BasePackageKey, it) }
    }

private fun String?.combinePackage(resourcePackage: String?) = when {
    this == null -> resourcePackage
    resourcePackage == null -> this
    else -> "$this.$resourcePackage"
}

fun Route.resource(remotePath: String, relativePath: String = remotePath, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get(remotePath) {
        val content = call.resolveResource(relativePath, packageName)
        if (content != null)
            call.respond(content)
    }
}

fun Route.resources(resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get("{$pathParameterName...}") {
        val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
        val content = call.resolveResource(relativePath, packageName)
        if (content != null)
            call.respond(content)
    }
}

fun Route.defaultResource(relativePath: String, resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get {
        val content = call.resolveResource(relativePath, packageName)
        if (content != null)
            call.respond(content)
    }
}
