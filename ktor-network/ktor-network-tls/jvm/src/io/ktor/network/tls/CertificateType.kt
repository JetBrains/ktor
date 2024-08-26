/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

/**
 * Type of client certificate.
 * see also https://tools.ietf.org/html/rfc5246#section-7.4.4
 *
 * @property code numeric algorithm codes
 */
internal object CertificateType {
    const val RSA: Byte = 1
    const val DSS: Byte = 2
    const val RSA_FIXED_DH: Byte = 3
    const val DSS_FIXED_DH: Byte = 4
    const val RSA_EPHEMERAL_DH_RESERVED: Byte = 5
    const val DSS_EPHEMERAL_DH_RESERVED: Byte = 6
    const val FORTEZZA_DMS_RESERVED: Byte = 20
}
