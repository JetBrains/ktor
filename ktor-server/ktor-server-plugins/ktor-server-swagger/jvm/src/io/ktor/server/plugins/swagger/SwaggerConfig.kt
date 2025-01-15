/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    /**
     * Whether to allow deep linking in Swagger UI, as described here: https://swagger.io/docs/open-source-tools/swagger-ui/usage/deep-linking/
     *
     * Defaults to `false`.
     */
    public var deepLinking: Boolean = false

    /*
     * Swagger favicon location
     */
    public var faviconLocation: String = "https://unpkg.com/swagger-ui-dist@$version/favicon-32x32.png"
}
