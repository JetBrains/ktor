/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.*

/**
 * Base class for implementing an [PipelineCall].
 */
public abstract class BaseApplicationCall(final override val application: Application) : PipelineCall {
    public final override val attributes: Attributes = Attributes()
    override val parameters: Parameters get() = request.queryParameters

    public abstract override val request: BaseApplicationRequest
    public abstract override val response: BaseApplicationResponse

    /**
     * Put engine response attribute. This is required for base implementation to work properly
     */
    protected fun putResponseAttribute(response: BaseApplicationResponse = this.response) {
        attributes.put(BaseApplicationResponse.EngineResponseAttributeKey, response)
    }
}
