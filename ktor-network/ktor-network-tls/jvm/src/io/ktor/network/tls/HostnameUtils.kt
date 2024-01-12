/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.http.*
import java.security.cert.*

private const val DNS_NAME_TYPE: Int = 2
private const val IP_ADDRESS_TYPE: Int = 7

internal fun verifyHostnameInCertificate(serverName: String, certificate: X509Certificate) {
    if (hostIsIp(serverName)) {
        verifyIpInCertificate(serverName, certificate)
        return
    }

    val hosts = certificate.hosts()
    if (hosts.isEmpty()) return
    if (hosts.any { matchHostnameWithCertificate(serverName, it) }) return

    throw TLSException(
        "No server host: $serverName in the server certificate. " +
            "Provided in certificate: ${hosts.joinToString()}"
    )
}

internal fun verifyIpInCertificate(ipString: String, certificate: X509Certificate) {
    val ips = certificate.subjectAlternativeNames
        .filter { it[0] as Int == IP_ADDRESS_TYPE }
        .map { it[1] as String }

    if (ips.isEmpty()) return
    if (ips.any { it == ipString }) return

    throw TLSException(
        "No server host: $ipString in the server certificate." +
            " The certificate was issued for: ${ips.joinToString()}."
    )
}

internal fun matchHostnameWithCertificate(serverName: String, certificateHost: String): Boolean {
    if (serverName.equals(certificateHost, ignoreCase = true)) return true

    val nameChunks = serverName.split('.').asReversed()
    val certificateChunks = certificateHost.split('.').asReversed()

    var nameIndex = 0
    var certificateIndex = 0
    var wildcardFound = false
    var labels = 0

    while (nameIndex < nameChunks.size && certificateIndex < certificateChunks.size) {
        val nameChunk = nameChunks[nameIndex]

        // skip absolute dot
        if (nameIndex == 0 && nameChunk.isEmpty()) {
            nameIndex++
            continue
        }

        val certificateChunk = certificateChunks[certificateIndex]

        // skip absolute dot
        if (certificateIndex == 0 && certificateChunk.isEmpty()) {
            certificateIndex++
            continue
        }

        if (!wildcardFound && nameChunk.equals(certificateChunk, ignoreCase = true)) {
            labels++
            nameIndex++
            certificateIndex++
            continue
        }

        if (certificateChunk == "*") {
            wildcardFound = true

            nameIndex += 1
            certificateIndex += 1
            continue
        }

        return false
    }

    val wildcardUsedCorrect = !wildcardFound || labels >= 2

    return (nameIndex == nameChunks.size && certificateIndex == certificateChunks.size) && wildcardUsedCorrect
}

private fun X509Certificate.hosts(): List<String> = subjectAlternativeNames
    ?.filter { it[0] as Int == DNS_NAME_TYPE }
    ?.map { it[1] as String }
    ?: emptyList()

private fun X509Certificate.ips(): List<String> = subjectAlternativeNames
    ?.filter { it[0] as Int == IP_ADDRESS_TYPE }
    ?.map { it[1] as String }
    ?: emptyList()
