/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.tests.*

class ApacheHttpsTest : HttpsTest<ApacheEngineConfig>(Apache) {

    override fun ApacheEngineConfig.disableCertificatePinning() {
        this.sslContext = this@ApacheHttpsTest.unsafeSslContext
    }
}
