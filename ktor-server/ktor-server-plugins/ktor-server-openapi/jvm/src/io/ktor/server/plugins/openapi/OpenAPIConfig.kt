/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.swagger.codegen.v3.*
import io.swagger.codegen.v3.generators.html.*
import io.swagger.parser.*
import io.swagger.v3.parser.core.models.*

/**
 * Configuration for OpenAPI endpoint.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.plugins.openapi.OpenAPIConfig)
 */
public class OpenAPIConfig {
    /**
     * Specifies a parser used to parse OpenAPI.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.plugins.openapi.OpenAPIConfig.parser)
     */
    public var parser: OpenAPIParser = OpenAPIParser()

    /**
     * Specifies options of the OpenAPI generator.
     */
    public var opts: ClientOptInput = ClientOptInput()

    /**
     * Specifies a generator used to generate OpenAPI.
     */
    public var generator: Generator = DefaultGenerator()

    /**
     * Specifies a code generator for [OpenAPIConfig].
     *
     * See also [StaticHtml2Codegen], [StaticHtmlCodegen] and etc.
     */
    public var codegen: CodegenConfig = StaticHtml2Codegen()

    /**
     * Provides access to options of the OpenAPI format parser.
     */
    public var options: ParseOptions = ParseOptions()
}
