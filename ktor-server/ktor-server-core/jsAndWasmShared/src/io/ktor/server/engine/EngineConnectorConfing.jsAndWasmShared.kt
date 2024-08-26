/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

/**
 * Returns new instance of [EngineConnectorConfig] based on [this] with modified port
 */
public actual fun EngineConnectorConfig.withPort(
    otherPort: Int
): EngineConnectorConfig = object : EngineConnectorConfig by this {
    override val port: Int = otherPort
}
