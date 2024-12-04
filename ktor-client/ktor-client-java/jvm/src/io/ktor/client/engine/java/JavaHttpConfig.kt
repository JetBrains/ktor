/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.engine.*
import java.net.http.*

/**
 * A configuration for the [Java] client engine.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.java.JavaHttpConfig)
 */
public class JavaHttpConfig : HttpClientEngineConfig() {

    /**
     * An HTTP version to use.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.client.engine.java.JavaHttpConfig.protocolVersion)
     */
    public var protocolVersion: HttpClient.Version = HttpClient.Version.HTTP_1_1

    internal var config: HttpClient.Builder.() -> Unit = {
        followRedirects(HttpClient.Redirect.NEVER)
    }

    /**
     * Configure [HttpClient] using [HttpClient.Builder].
     */
    public fun config(block: HttpClient.Builder.() -> Unit) {
        val oldConfig = config
        config = {
            oldConfig()
            block()
        }
    }
}
