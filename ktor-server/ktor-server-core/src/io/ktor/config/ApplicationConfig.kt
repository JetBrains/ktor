package io.ktor.config

import io.ktor.util.*

/**
 * Represents an application config node
 */
@KtorExperimentalAPI
interface ApplicationConfig {
    /**
     * Get config property with [path], return [default] or fail if no [default] provided
     * @throws ApplicationConfigurationException
     */
    fun property(path: String, default: ApplicationConfigValue? = null): ApplicationConfigValue

    /**
     * Get config property value for [path], return [default] or return `null`
     */
    fun propertyOrNull(path: String, default: ApplicationConfigValue? = null): ApplicationConfigValue?

    /**
     * Get config child node or fail
     * @throws ApplicationConfigurationException
     */
    fun config(path: String): ApplicationConfig

    /**
     * Get a list of child nodes for [path] or fail
     * @throws ApplicationConfigurationException
     */
    fun configList(path: String): List<ApplicationConfig>
}

/**
 * Represents an application config value
 */
@KtorExperimentalAPI
interface ApplicationConfigValue {
    /**
     * Get property string value
     */
    fun getString(): String

    /**
     * Get property list value
     */
    fun getList(): List<String>
}

/**
 * Thrown when an application is misconfigured
 */
@KtorExperimentalAPI
class ApplicationConfigurationException(message: String) : Exception(message)
