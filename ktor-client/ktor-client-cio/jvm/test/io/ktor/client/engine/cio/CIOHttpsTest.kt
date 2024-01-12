/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.tests.*

class CIOHttpsTest : HttpsTest<CIOEngineConfig>(CIO) {

    override fun CIOEngineConfig.disableCertificatePinning() {
        https {
            trustManager = trustAllCertificates[0]
        }
    }
}
