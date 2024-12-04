/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.*
import java.nio.file.*
import java.util.jar.*

/**
 * Represents an [OutgoingContent] for a resource inside a Jar file
 *
 * @param jarFile is an instance of [File] representing a Jar
 * @param resourcePath is an instance of a resource inside a Jar file
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.http.content.JarFileContent)
 */
public class JarFileContent(
    public val jarFile: File,
    public val resourcePath: String,
    override val contentType: ContentType
) : OutgoingContent.ReadChannelContent() {

    private val normalized = File(resourcePath).normalize().toString().replace(File.separatorChar, '/')
    private val jarEntry: JarEntry? by lazy(LazyThreadSafetyMode.NONE) { jar.getJarEntry(resourcePath) }
    private val jar by lazy(LazyThreadSafetyMode.NONE) { JarFile(jarFile) }

    public val isFile: Boolean by lazy(LazyThreadSafetyMode.NONE) { jarEntry?.isDirectory?.not() ?: false }

    public constructor(zipFilePath: Path, resourcePath: String, contentType: ContentType) : this(
        zipFilePath.toFile(),
        resourcePath,
        contentType
    )

    init {
        require(!normalized.startsWith("..")) { "Bad resource relative path $resourcePath" }
        jarEntry?.let { versions += LastModifiedVersion(it.lastModifiedTime) }
    }

    override val contentLength: Long? get() = jarEntry?.size

    override fun readFrom(): ByteReadChannel {
        val jarEntry = jarEntry ?: throw IOException("Resource $normalized not found")
        return jar.getInputStream(jarEntry).toByteReadChannel(pool = KtorDefaultPool)
    }
}
