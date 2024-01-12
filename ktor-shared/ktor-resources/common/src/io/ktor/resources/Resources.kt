/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources

import io.ktor.resources.serialization.*
import io.ktor.utils.io.*
import kotlinx.serialization.modules.*

/**
 * Resources plugin instance.
 */
public class Resources(configuration: Configuration) {

    /**
     * The format instance used to (de)serialize resources instances
     */
    public val resourcesFormat: ResourcesFormat = ResourcesFormat(configuration.serializersModule)

    /**
     * Configuration for the Resources plugin instance.
     */
    @KtorDsl
    public class Configuration {

        /**
         * [SerializersModule] used to (de)serialize the Resource instances.
         */
        public var serializersModule: SerializersModule = EmptySerializersModule()
    }
}
