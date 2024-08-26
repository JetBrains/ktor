/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.config

/**
 * Represents an application config node
 */
public interface ApplicationConfig {
    /**
     * Get config property with [path] or fail
     * @throws ApplicationConfigurationException
     */
    public fun property(path: String): ApplicationConfigValue

    /**
     * Get config property value for [path] or return `null`
     */
    public fun propertyOrNull(path: String): ApplicationConfigValue?

    /**
     * Get config child node or fail
     * @throws ApplicationConfigurationException
     */
    public fun config(path: String): ApplicationConfig

    /**
     * Get a list of child nodes for [path] or fail
     * @throws ApplicationConfigurationException
     */
    public fun configList(path: String): List<ApplicationConfig>

    /**
     * Returns the set of keys, found by recursing the root object.
     * All entries represent leaf nodes' keys, meaning that there would be no nested
     * objects directly included as values for returned keys.
     * It's still possible that entries may be a list and the lists may contain objects.
     * @return set of paths with non-null values, built up by recursing the entire tree of
     * config and creating an entry for each leaf value.
     */
    public fun keys(): Set<String>

    /**
     * Returns map representation of this config.
     * Values can be `String`, `Map<String, Any>`, `List<String>` and `List<Map<String, Any>>`
     */
    public fun toMap(): Map<String, Any?>
}

/**
 * Represents an application config value
 */
public interface ApplicationConfigValue {
    /**
     * Get property string value
     */
    public fun getString(): String

    /**
     * Get property list value
     */
    public fun getList(): List<String>
}

/**
 * Thrown when an application is misconfigured
 */
public class ApplicationConfigurationException(message: String, cause: Throwable?) : Exception(message, cause) {
    public constructor(message: String) : this(message, null)
}

/**
 * Try read String value from [ApplicationConfig].
 *
 * @return null if key is missing
 */
public fun ApplicationConfig.tryGetString(key: String): String? =
    propertyOrNull(key)?.getString()

/**
 * Try read String value from [ApplicationConfig].
 *
 * @return null if key is missing
 */
public fun ApplicationConfig.tryGetStringList(key: String): List<String>? =
    propertyOrNull(key)?.getList()
