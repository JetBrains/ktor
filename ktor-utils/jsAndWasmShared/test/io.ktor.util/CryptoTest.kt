/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.test.*

private fun checkMsCrypto(): Unit = js("window.msCrypto = window.crypto")
private fun defineCrypto(): Unit = js("Object.defineProperty(window, 'crypto', {writable: true})")
private fun deleteCrypto(): Unit = js("delete window.crypto")

class CryptoTest {
    @Test
    fun expectCryptoToBeDefinedInIE11() {
        if (!PlatformUtils.IS_BROWSER) return

        checkMsCrypto()
        defineCrypto()
        deleteCrypto()
        assertEquals(32, generateNonce().length)
    }
}
