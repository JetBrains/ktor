/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.client.engine.darwin.*

@Deprecated(
    "Please use 'Darwin' engine instead",
    replaceWith = ReplaceWith("Darwin", "io.ktor.client.engine.darwin.Darwin"),
    level = DeprecationLevel.ERROR
)
public typealias Ios = Darwin

@Deprecated(
    "Please use 'Darwin' engine instead",
    replaceWith = ReplaceWith("DarwinClientEngineConfig", "io.ktor.client.engine.darwin.DarwinClientEngineConfig"),
    level = DeprecationLevel.ERROR
)
public typealias IosClientEngineConfig = DarwinClientEngineConfig

@Deprecated(
    "Please use 'Darwin' engine instead",
    replaceWith = ReplaceWith("DarwinHttpRequestException", "io.ktor.client.engine.darwin.DarwinHttpRequestException"),
    level = DeprecationLevel.ERROR
)
public typealias IosHttpRequestException = DarwinHttpRequestException
