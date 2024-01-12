/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import io.ktor.server.config.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.*
import net.mamoe.yamlkt.*
import platform.posix.*

private fun init() {
    addConfigLoader(YamlConfigLoader())
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val initHook = init()

/**
 * Loads a configuration from the YAML file, if found.
 * On JVM, loads a configuration from application resources, if exist; otherwise, reads a configuration from a file.
 * On Native, always reads a configuration from a file.
 */
public actual fun YamlConfig(path: String?): YamlConfig? {
    val resolvedPath = when {
        path != null && path.endsWith(".yaml") -> path
        path == null && access(DEFAULT_YAML_FILENAME, F_OK) == 0 -> DEFAULT_YAML_FILENAME
        else -> null
    } ?: return null
    val content = readFile(resolvedPath)
    val yaml = Yaml.decodeYamlFromString(content) as? YamlMap
        ?: throw ApplicationConfigurationException("$resolvedPath should be a YAML dictionary")
    @Suppress("DEPRECATION")
    return YamlConfig(yaml).apply { checkEnvironmentVariables() }
}

@OptIn(ExperimentalForeignApi::class)
private fun readFile(path: String): String {
    val fileDescriptor = fopen(path, "rb") ?: throw ApplicationConfigurationException("Can not read $path")
    val bytes = ByteArrayPool.borrow()
    val size = bytes.size
    var read: Int
    val packet = buildPacket {
        do {
            read = fileDescriptor.readFileChunk(bytes, size)
            writeFully(bytes, 0, read)
        } while (read > 0)
    }
    ByteArrayPool.recycle(bytes)
    val error = ferror(fileDescriptor)
    if (error != 0) {
        fclose(fileDescriptor)
        throw ApplicationConfigurationException("Can not read $path. Error $error")
    }
    if (fclose(fileDescriptor) != 0) {
        throw ApplicationConfigurationException("Can not read $path", PosixException.forErrno())
    }
    return packet.readText()
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun CPointer<FILE>.readFileChunk(
    bytes: ByteArray,
    size: Int
): Int = bytes.usePinned { pinned ->
    fread(pinned.addressOf(0), 1.convert(), size.convert(), this)
}.convert()

@OptIn(ExperimentalForeignApi::class)
internal actual fun getEnvironmentValue(key: String): String? = getenv(key)?.toKString()
