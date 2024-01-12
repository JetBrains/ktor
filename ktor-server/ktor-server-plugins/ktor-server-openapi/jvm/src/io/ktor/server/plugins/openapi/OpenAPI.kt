/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.swagger.codegen.v3.*
import io.swagger.codegen.v3.generators.html.*
import java.io.*

/**
 * Creates a `get` endpoint at [path] with documentation rendered from the OpenAPI file.
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [java.io.File].
 *
 * The documentation is generated using [StaticHtml2Codegen] by default. It can be customized using config in [block].
 * See [OpenAPIConfig] for more details.
 */
public fun Route.openAPI(
    path: String,
    swaggerFile: String = "openapi/documentation.yaml",
    block: OpenAPIConfig.() -> Unit = {}
) {
    val apiDocument = readOpenAPIFile(swaggerFile, environment.classLoader)

    val config = OpenAPIConfig()
    with(config) {
        val swagger = parser.readContents(apiDocument, null, options)
        File("docs").mkdirs()

        opts.apply {
            config(codegen)
            opts(ClientOpts())
            openAPI(swagger.openAPI)
        }

        block(this)

        generator.opts(opts)
        generator.generate()

        staticFiles(path, File("docs"))
    }
}

internal fun readOpenAPIFile(swaggerFile: String, classLoader: ClassLoader): String {
    val resource = classLoader.getResourceAsStream(swaggerFile)
        ?.bufferedReader()?.readText()

    if (resource != null) return resource

    val file = File(swaggerFile)
    if (!file.exists()) {
        throw FileNotFoundException("Swagger file not found: $swaggerFile")
    }

    return file.readText()
}
