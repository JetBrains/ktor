/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

/**
 * A configuration for the Swagger UI endpoint.
 */
public class SwaggerConfig {
    internal var customStyle: String? = null

    /**
     * Specifies a Swagger UI version to use.
     */
    public var version: String = "5.17.12"

    /**
     * Specifies a URL for a custom CSS applied to a Swagger UI.
     *
     * Example: https://unpkg.com/swagger-ui-themes@3.0.1/themes/3.x/theme-monokai.css
     */
    public fun customStyle(path: String?) {
        customStyle = path
    }

    /**
     * Swagger package location
     */
    public var packageLocation: String = "https://unpkg.com/swagger-ui-dist"
}
