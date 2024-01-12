/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.io.*

/**
 * Serves a single-page application.
 * You can learn more from [Serving single-page applications](https://ktor.io/docs/serving-spa.html).
 *
 * A basic configuration for the application served from the `filesPath` folder
 * with `index.html` as a default file:
 *
 * ```
 * application {
 *     routing {
 *        singlePageApplication {
 *           filesPath = "application/project_path"
 *         }
 *     }
 * }
 * ```
 */
public fun Route.singlePageApplication(configBuilder: SPAConfig.() -> Unit = {}) {
    val config = SPAConfig()
    configBuilder.invoke(config)

    if (config.useResources) {
        staticResources(config.applicationRoute, config.filesPath, index = config.defaultPage) {
            default(config.defaultPage)
            config.ignoredFiles.forEach { ignoreConfig ->
                exclude { url ->
                    ignoreConfig(url.path)
                }
            }
        }
    } else {
        staticFiles(config.applicationRoute, File(config.filesPath), index = config.defaultPage) {
            default(config.defaultPage)
            config.ignoredFiles.forEach { ignoreConfig ->
                exclude { url ->
                    ignoreConfig(url.path)
                }
            }
        }
    }
}

/**
 * Configuration for the [Route.singlePageApplication] plugin.
 */
public class SPAConfig(
    /**
     * The default name of a file or resource to serve when path inside [applicationRoute] is requested
     */
    public var defaultPage: String = "index.html",

    /**
     * The URL path under which the content should be served
     */
    public var applicationRoute: String = "/",

    /**
     * The path under which the static content is located.
     * Corresponds to the folder path if the [useResources] is false, a resource path otherwise
     */
    public var filesPath: String = "",

    /**
     * Specifies if static content is a resource package with `true` or folder with `false`
     */
    public var useResources: Boolean = false,

    /**
     * A list of callbacks checking if a file or resource in [filesPath] is ignored.
     * Requests for such files or resources fail with the 403 Forbidden status code
     */
    internal val ignoredFiles: MutableList<(String) -> Boolean> = mutableListOf()
)

/**
 * Registers a [block] in [ignoredFiles]
 * [block] returns true if [path] should be ignored.
 */
public fun SPAConfig.ignoreFiles(block: (path: String) -> Boolean) {
    ignoredFiles += block
}

/**
 * Creates an application configuration for the Angular project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SPAConfig.angular(filesPath: String) {
    this.filesPath = filesPath
}

/**
 * Creates an application configuration for the React project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SPAConfig.react(filesPath: String) {
    this.filesPath = filesPath
}

/**
 * Creates an application configuration for the Vue project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SPAConfig.vue(filesPath: String) {
    this.filesPath = filesPath
}

/**
 * Creates an application configuration for the Ember project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SPAConfig.ember(filesPath: String) {
    this.filesPath = filesPath
}

/**
 * Creates an application configuration for the Backbone project.
 * Resources will be shared from the filesPath directory. The root file is index.html
 */
public fun SPAConfig.backbone(filesPath: String) {
    this.filesPath = filesPath
}
