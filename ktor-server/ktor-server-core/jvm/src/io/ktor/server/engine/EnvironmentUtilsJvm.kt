/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.*
import java.io.*
import java.net.*
import java.security.*

internal actual fun ApplicationEngine.Configuration.configureSSLConnectors(
    host: String,
    sslPort: String,
    sslKeyStorePath: String?,
    sslKeyStorePassword: String?,
    sslPrivateKeyPassword: String?,
    sslKeyAlias: String
) {
    if (sslKeyStorePath == null) {
        throw IllegalArgumentException(
            "SSL requires keystore: use -sslKeyStore=path or ${ConfigKeys.hostSslKeyStore} config"
        )
    }
    if (sslKeyStorePassword == null) {
        throw IllegalArgumentException(
            "SSL requires keystore password: use ${ConfigKeys.hostSslKeyStorePassword} config"
        )
    }
    if (sslPrivateKeyPassword == null) {
        throw IllegalArgumentException(
            "SSL requires certificate password: use ${ConfigKeys.hostSslPrivateKeyPassword} config"
        )
    }

    val keyStoreFile = File(sslKeyStorePath).let { file ->
        if (file.exists() || file.isAbsolute) file else File(".", sslKeyStorePath).absoluteFile
    }
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        FileInputStream(keyStoreFile).use {
            load(it, sslKeyStorePassword.toCharArray())
        }

        requireNotNull(getKey(sslKeyAlias, sslPrivateKeyPassword.toCharArray())) {
            "The specified key $sslKeyAlias doesn't exist in the key store $sslKeyStorePath"
        }
    }

    sslConnector(
        keyStore,
        sslKeyAlias,
        { sslKeyStorePassword.toCharArray() },
        { sslPrivateKeyPassword.toCharArray() }
    ) {
        this.host = host
        this.port = sslPort.toInt()
        this.keyStorePath = keyStoreFile
    }
}

internal actual fun ApplicationEnvironmentBuilder.configurePlatformProperties(args: Array<String>) {
    val argumentsPairs = args.mapNotNull { it.splitPair('=') }.toMap()
    val jar = argumentsPairs["-jar"]?.let {
        when {
            it.startsWith("file:") || it.startsWith("jrt:") || it.startsWith("jar:") -> URI(it).toURL()
            else -> File(it).toURI().toURL()
        }
    }

    classLoader = jar?.let { URLClassLoader(arrayOf(jar), ApplicationEnvironment::class.java.classLoader) }
        ?: ApplicationEnvironment::class.java.classLoader
}

internal actual fun getKtorEnvironmentProperties(): List<Pair<String, String>> = buildList {
    System.getProperties().forEach {
        val key = it.key as? String ?: return@forEach
        if (key.startsWith("ktor.")) {
            val value = it.value as? String ?: return@forEach
            add(key to value)
        }
    }
}

internal actual fun getEnvironmentProperty(key: String): String? {
    return System.getProperty(key)
}

internal actual fun setEnvironmentProperty(key: String, value: String) {
    System.setProperty(key, value)
}

internal actual fun clearEnvironmentProperty(key: String) {
    System.clearProperty(key)
}
