/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.util.*
import io.ktor.util.logging.*

public class CommandLineConfig(
    public val applicationProperties: ApplicationProperties,
    public val engineConfig: BaseApplicationEngine.Configuration
) {
    public val environment: ApplicationEnvironment = applicationProperties.environment
}

public object ConfigKeys {
    public const val applicationIdPath: String = "ktor.application.id"
    public const val hostConfigPath: String = "ktor.deployment.host"
    public const val hostPortPath: String = "ktor.deployment.port"
    public const val hostWatchPaths: String = "ktor.deployment.watch"

    public const val rootPathPath: String = "ktor.deployment.rootPath"

    public const val hostSslPortPath: String = "ktor.deployment.sslPort"
    public const val hostSslKeyStore: String = "ktor.security.ssl.keyStore"
    public const val hostSslKeyAlias: String = "ktor.security.ssl.keyAlias"
    public const val hostSslKeyStorePassword: String = "ktor.security.ssl.keyStorePassword"
    public const val hostSslPrivateKeyPassword: String = "ktor.security.ssl.privateKeyPassword"
    public const val developmentModeKey: String = "ktor.development"
}

/**
 * Creates an [ApplicationEnvironment] instance from command line arguments
 */
public fun CommandLineConfig(args: Array<String>): CommandLineConfig {
    val argumentsPairs = args.mapNotNull { it.splitPair('=') }
    val argumentsMap = argumentsPairs.toMap()
    val configuration = buildApplicationConfig(argumentsPairs)
    val applicationId = configuration.tryGetString(ConfigKeys.applicationIdPath) ?: "Application"
    val logger = KtorSimpleLogger(applicationId)

    val environment = applicationEnvironment {
        log = logger

        configurePlatformProperties(args)

        config = configuration
    }

    val applicationProperties = applicationProperties(environment) {
        rootPath = argumentsMap["-path"] ?: configuration.tryGetString(ConfigKeys.rootPathPath) ?: ""
        developmentMode = configuration.tryGetString(ConfigKeys.developmentModeKey)
            ?.let { it.toBoolean() } ?: PlatformUtils.IS_DEVELOPMENT_MODE

        (argumentsMap["-watch"]?.split(",") ?: configuration.tryGetStringList(ConfigKeys.hostWatchPaths))?.let {
            watchPaths = it
        }
    }

    val host = argumentsMap["-host"] ?: configuration.tryGetString(ConfigKeys.hostConfigPath) ?: "0.0.0.0"
    val port = argumentsMap["-port"] ?: configuration.tryGetString(ConfigKeys.hostPortPath)
    val sslPort = argumentsMap["-sslPort"] ?: configuration.tryGetString(ConfigKeys.hostSslPortPath)
    val sslKeyStorePath = argumentsMap["-sslKeyStore"] ?: configuration.tryGetString(ConfigKeys.hostSslKeyStore)
    val sslKeyStorePassword = configuration.tryGetString(ConfigKeys.hostSslKeyStorePassword)?.trim()
    val sslPrivateKeyPassword = configuration.tryGetString(ConfigKeys.hostSslPrivateKeyPassword)?.trim()
    val sslKeyAlias = configuration.tryGetString(ConfigKeys.hostSslKeyAlias) ?: "mykey"

    if (port == null && sslPort == null) {
        throw IllegalArgumentException(
            "Neither port nor sslPort specified. Use command line options -port/-sslPort " +
                "or configure connectors in application.conf"
        )
    }

    val engineConfig = BaseApplicationEngine.Configuration()
    if (port != null) {
        engineConfig.connector {
            this.host = host
            this.port = port.toInt()
        }
    }
    if (sslPort != null) {
        engineConfig.configureSSLConnectors(
            host,
            sslPort,
            sslKeyStorePath,
            sslKeyStorePassword,
            sslPrivateKeyPassword,
            sslKeyAlias
        )
    }

    return CommandLineConfig(applicationProperties, engineConfig)
}

internal fun buildApplicationConfig(args: List<Pair<String, String>>): ApplicationConfig {
    val commandLineProperties = args
        .filter { it.first.startsWith("-P:") }
        .map { it.first.removePrefix("-P:") to it.second }

    val configPaths = args.filter { it.first == "-config" }.map { it.second }

    val commandLineConfig = MapApplicationConfig(commandLineProperties)
    val environmentConfig = getConfigFromEnvironment()

    val fileConfig = when (configPaths.size) {
        0 -> ConfigLoader.load()
        1 -> ConfigLoader.load(configPaths.single())
        else -> configPaths.map { ConfigLoader.load(it) }.reduce { first, second -> first.mergeWith(second) }
    }

    return fileConfig.mergeWith(environmentConfig).mergeWith(commandLineConfig)
}

internal expect fun ApplicationEngine.Configuration.configureSSLConnectors(
    host: String,
    sslPort: String,
    sslKeyStorePath: String?,
    sslKeyStorePassword: String?,
    sslPrivateKeyPassword: String?,
    sslKeyAlias: String
)

internal expect fun ApplicationEnvironmentBuilder.configurePlatformProperties(args: Array<String>)

internal expect fun getConfigFromEnvironment(): ApplicationConfig

/**
 * Loads common engine configuration parameters applicable to all engine types from the specified [ApplicationConfig].
 *
 * @param deploymentConfig The application configuration.
 */
public fun ApplicationEngine.Configuration.loadCommonConfiguration(deploymentConfig: ApplicationConfig) {
    deploymentConfig.propertyOrNull("callGroupSize")?.getString()?.toInt()?.let {
        callGroupSize = it
    }
    deploymentConfig.propertyOrNull("connectionGroupSize")?.getString()?.toInt()?.let {
        connectionGroupSize = it
    }
    deploymentConfig.propertyOrNull("workerGroupSize")?.getString()?.toInt()?.let {
        workerGroupSize = it
    }
    deploymentConfig.propertyOrNull("shutdownGracePeriod")?.getString()?.toLong()?.let {
        shutdownGracePeriod = it
    }
    deploymentConfig.propertyOrNull("shutdownTimeout")?.getString()?.toLong()?.let {
        shutdownTimeout = it
    }
}

internal fun String.splitPair(ch: Char): Pair<String, String>? = indexOf(ch).let { idx ->
    when (idx) {
        -1 -> null
        else -> Pair(take(idx), drop(idx + 1))
    }
}
