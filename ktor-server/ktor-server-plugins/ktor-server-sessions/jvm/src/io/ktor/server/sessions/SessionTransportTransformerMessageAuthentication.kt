/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.util.*
import java.security.*
import javax.crypto.*
import javax.crypto.spec.*

/**
 * A transformer used to sign session data.
 * This transformer appends an [algorithm] MAC (Message Authentication Code) hash of the session data.
 * It uses a specified [keySpec] when generating the MAC hash.
 *
 * @see [Sessions]
 *
 * @property keySpec is a secret key spec for message authentication
 * @property algorithm is a message authentication algorithm name
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication)
 */
public class SessionTransportTransformerMessageAuthentication(
    public val keySpec: SecretKeySpec,
    public val algorithm: String = "HmacSHA256"
) : SessionTransportTransformer {
    public constructor(key: ByteArray, algorithm: String = "HmacSHA256") : this(
        SecretKeySpec(key, algorithm),
        algorithm
    )

    override fun transformRead(transportValue: String): String? {
        val expectedSignature = transportValue.substringAfterLast('/', "")
        val value = transportValue.substringBeforeLast('/')
        if (MessageDigest.isEqual(mac(value).toByteArray(), expectedSignature.toByteArray())) {
            return value
        }
        return null
    }

    override fun transformWrite(transportValue: String): String = "$transportValue/${mac(transportValue)}"

    private fun mac(value: String): String {
        val mac = Mac.getInstance(algorithm)
        mac.init(keySpec)

        return hex(mac.doFinal(value.toByteArray()))
    }
}
