/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.server.config.yaml

import io.ktor.server.config.*
import net.mamoe.yamlkt.*

internal const val DEFAULT_YAML_FILENAME = "application.yaml"

/**
 * Loads [ApplicationConfig] from a YAML file.
 */
public class YamlConfigLoader : ConfigLoader {
    /**
     * Tries loading an application configuration from the specified [path].
     *
     * @return configuration or null if the path is not found or a configuration format is not supported.
     */
    override fun load(path: String?): ApplicationConfig? {
        return YamlConfig(path)?.apply { checkEnvironmentVariables() }
    }
}

/**
 * Loads a configuration from the YAML file, if found.
 * On JVM, loads a configuration from application resources, if exists; otherwise, reads a configuration from a file.
 * On Native, always reads a configuration from a file.
 */
public expect fun YamlConfig(path: String?): YamlConfig?

/**
 * Implements [ApplicationConfig] by loading a configuration from a YAML file.
 * Values can reference to environment variables with `$ENV_VAR` or `"$ENV_VAR:default_value"` syntax.
 */
public class YamlConfig
@Deprecated("This will become internal")
constructor(
    private val yaml: YamlMap
) : ApplicationConfig {

    private var root: YamlConfig = this

    @Suppress("DEPRECATION")
    internal constructor(
        yaml: YamlMap,
        root: YamlConfig
    ) : this(yaml) {
        this.root = root
    }

    override fun property(path: String): ApplicationConfigValue {
        return propertyOrNull(path) ?: throw ApplicationConfigurationException("Path $path not found.")
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        val parts = path.split('.')
        val yaml = parts.dropLast(1).fold(yaml) { yaml, part -> yaml[part] as? YamlMap ?: return null }
        val value = yaml[parts.last()] ?: return null
        return when (value) {
            is YamlNull -> null
            is YamlLiteral -> resolveValue(value.content, root)?.let { LiteralConfigValue(key = path, value = it) }

            is YamlList -> {
                val values = value.content.map { element ->
                    element.asLiteralOrNull()?.content?.let { resolveValue(it, root) }
                        ?: throw ApplicationConfigurationException("Value at path $path can not be resolved.")
                }
                ListConfigValue(key = path, values = values)
            }

            else -> throw ApplicationConfigurationException(
                "Expected primitive or list at path $path, but was ${value::class}"
            )
        }
    }

    override fun config(path: String): ApplicationConfig {
        val parts = path.split('.')
        val yaml = parts.fold(yaml) { yaml, part ->
            yaml[part] as? YamlMap ?: throw ApplicationConfigurationException("Path $path not found.")
        }
        return YamlConfig(yaml, root)
    }

    override fun configList(path: String): List<ApplicationConfig> {
        val parts = path.split('.')
        val yaml = parts.dropLast(1).fold(yaml) { yaml, part ->
            yaml[part] as? YamlMap ?: throw ApplicationConfigurationException("Path $path not found.")
        }
        val value = yaml[parts.last()] as? YamlList ?: throw ApplicationConfigurationException("Path $path not found.")
        return value.map {
            YamlConfig(
                it as? YamlMap
                    ?: throw ApplicationConfigurationException("Property $path is not a list of maps."),
                root
            )
        }
    }

    override fun keys(): Set<String> {
        fun keys(yaml: YamlMap): Set<String> {
            return yaml.keys.map { it.content as String }
                .flatMap { key ->
                    when (val value = yaml[key]) {
                        is YamlMap -> keys(value).map { "$key.$it" }
                        else -> listOf(key)
                    }
                }
                .toSet()
        }
        return keys(yaml)
    }

    public override fun toMap(): Map<String, Any?> {
        fun toPrimitive(yaml: YamlElement?): Any? = when (yaml) {
            is YamlLiteral -> resolveValue(yaml.content, root)
            is YamlMap -> yaml.keys.associate { it.content as String to toPrimitive(yaml[it]) }
            is YamlList -> yaml.content.map { toPrimitive(it) }
            YamlNull -> null
            null -> null
        }

        val primitive = toPrimitive(yaml)
        @Suppress("UNCHECKED_CAST")
        return primitive as? Map<String, Any?> ?: throw IllegalStateException("Top level element is not a map")
    }

    public fun checkEnvironmentVariables() {
        fun check(element: YamlElement?) {
            when (element) {
                is YamlLiteral -> resolveValue(element.content, root)
                YamlNull -> return
                is YamlMap -> element.forEach { entry -> check(entry.value) }
                is YamlList -> element.forEach { check(it) }
                null -> return
            }
        }
        check(yaml)
    }

    private class LiteralConfigValue(private val key: String, private val value: String) : ApplicationConfigValue {
        override fun getString(): String = value

        override fun getList(): List<String> =
            throw ApplicationConfigurationException("Property $key is not a list of primitives.")
    }

    private class ListConfigValue(private val key: String, private val values: List<String>) : ApplicationConfigValue {
        override fun getString(): String =
            throw ApplicationConfigurationException("Property $key doesn't exist or not a primitive.")

        override fun getList(): List<String> = values
    }
}

private fun resolveValue(value: String, root: YamlConfig): String? {
    val isEnvVariable = value.startsWith("\$")
    if (!isEnvVariable) return value
    val keyWithDefault = value.drop(1)
    val separatorIndex = keyWithDefault.indexOf(':')

    if (separatorIndex != -1) {
        val key = keyWithDefault.substring(0, separatorIndex)
        return getEnvironmentValue(key) ?: keyWithDefault.substring(separatorIndex + 1)
    }

    val selfReference = root.propertyOrNull(keyWithDefault)
    if (selfReference != null) {
        return selfReference.getString()
    }

    val isOptional = keyWithDefault.first() == '?'
    val key = if (isOptional) keyWithDefault.drop(1) else keyWithDefault
    return getEnvironmentValue(key) ?: if (isOptional) {
        null
    } else {
        throw ApplicationConfigurationException(
            "Required environment variable \"$key\" not found and no default value is present"
        )
    }
}

internal expect fun getEnvironmentValue(key: String): String?
