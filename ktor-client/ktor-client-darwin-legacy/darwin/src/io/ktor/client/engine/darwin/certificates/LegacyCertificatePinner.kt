/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.darwin.certificates

import io.ktor.client.engine.darwin.*
import io.ktor.client.engine.darwin.internal.legacy.*
import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

/**
 * Constrains which certificates are trusted. Pinning certificates defends against attacks on
 * certificate authorities. It also prevents connections through man-in-the-middle certificate
 * authorities either known or unknown to the application's user.
 * This class currently pins a certificate's Subject Public Key Info as described on
 * [Adam Langley's Weblog](http://goo.gl/AIx3e5). Pins are either base64 SHA-256 hashes as in
 * [HTTP Public Key Pinning (HPKP)](http://tools.ietf.org/html/rfc7469) or SHA-1 base64 hashes as
 * in Chromium's [static certificates](http://goo.gl/XDh6je).
 *
 * ## Setting up Certificate Pinning
 *
 * The easiest way to pin a host is to turn on pinning with a broken configuration and read the
 * expected configuration when the connection fails. Be sure to do this on a trusted network, and
 * without man-in-the-middle tools like [Charles](http://charlesproxy.com) or
 * [Fiddler](http://fiddlertool.com).
 *
 * For example, to pin `https://publicobject.com`, start with a broken configuration:
 *
 * ```
 * HttpClient(Darwin) {
 *
 *     // ...
 *
 *     engine {
 *         val builder = CertificatePinner.Builder()
 *             .add("publicobject.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
 *         handleChallenge(builder.build())
 *     }
 * }
 * ```
 *
 * As expected, this fails with an exception, see the logs:
 *
 * ```
 * HttpClient: Certificate pinning failure!
 *   Peer certificate chain:
 *     sha256/afwiKY3RxoMmLkuRW1l7QsPZTJPwDS2pdDROQjXw8ig=: publicobject.com
 *     sha256/klO23nT2ehFDXCfx3eHTDRESMz3asj1muO+4aIdjiuY=: COMODO RSA Secure Server CA
 *     sha256/grX4Ta9HpZx6tSHkmCrvpApTQGo67CYDnvprLg5yRME=: COMODO RSA Certification Authority
 *     sha256/lCppFqbkrlJ3EcVFAkeip0+44VaoJUymbnOaEUk7tEU=: AddTrust External CA Root
 *   Pinned certificates for publicobject.com:
 *     sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
 * ```
 *
 * Follow up by pasting the public key hashes from the logs into the
 * certificate pinner's configuration:
 *
 * ```
 * val builder = CertificatePinner.Builder()
 *     .add("publicobject.com", "sha256/afwiKY3RxoMmLkuRW1l7QsPZTJPwDS2pdDROQjXw8ig=")
 *     .add("publicobject.com", "sha256/klO23nT2ehFDXCfx3eHTDRESMz3asj1muO+4aIdjiuY=")
 *     .add("publicobject.com", "sha256/grX4Ta9HpZx6tSHkmCrvpApTQGo67CYDnvprLg5yRME=")
 *     .add("publicobject.com", "sha256/lCppFqbkrlJ3EcVFAkeip0+44VaoJUymbnOaEUk7tEU=")
 * handleChallenge(builder.build())
 * ```
 *
 * ## Domain Patterns
 *
 * Pinning is per-hostname and/or per-wildcard pattern. To pin both `publicobject.com` and
 * `www.publicobject.com` you must configure both hostnames. Or you may use patterns to match
 * sets of related domain names. The following forms are permitted:
 *
 *  * **Full domain name**: you may pin an exact domain name like `www.publicobject.com`. It won't
 *    match additional prefixes (`us-west.www.publicobject.com`) or suffixes (`publicobject.com`).
 *
 *  * **Any number of subdomains**: Use two asterisks to like `**.publicobject.com` to match any
 *    number of prefixes (`us-west.www.publicobject.com`, `www.publicobject.com`) including no
 *    prefix at all (`publicobject.com`). For most applications this is the best way to configure
 *    certificate pinning.
 *
 *  * **Exactly one subdomain**: Use a single asterisk like `*.publicobject.com` to match exactly
 *    one prefix (`www.publicobject.com`, `api.publicobject.com`). Be careful with this approach as
 *    no pinning will be enforced if additional prefixes are present, or if no prefixes are present.
 *
 * Note that any other form is unsupported. You may not use asterisks in any position other than
 * the leftmost label.
 *
 * If multiple patterns match a hostname, any match is sufficient. For example, suppose pin A
 * applies to `*.publicobject.com` and pin B applies to `api.publicobject.com`. Handshakes for
 * `api.publicobject.com` are valid if either A's or B's certificate is in the chain.
 *
 * ## Warning: Certificate Pinning is Dangerous!
 *
 * Pinning certificates limits your server team's abilities to update their TLS certificates. By
 * pinning certificates, you take on additional operational complexity and limit your ability to
 * migrate between certificate authorities. Do not use certificate pinning without the blessing of
 * your server's TLS administrator!
 *
 * See also [OWASP: Certificate and Public Key Pinning](https://www.owasp.org/index
 * .php/Certificate_and_Public_Key_Pinning).
 *
 * This class was heavily inspired by OkHttp, which is a great Http library for Android
 * https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/
 * https://github.com/square/okhttp/blob/master/okhttp/src/main/java/okhttp3/CertificatePinner.kt
 */
@OptIn(UnsafeNumber::class)
public data class LegacyCertificatePinner internal constructor(
    private val pinnedCertificates: Set<LegacyPinnedCertificate>,
    private val validateTrust: Boolean
) : ChallengeHandler {

    @OptIn(ExperimentalForeignApi::class)
    override fun invoke(
        session: NSURLSession,
        task: NSURLSessionTask,
        challenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
    ) {
        val hostname = challenge.protectionSpace.host
        val matchingPins = findMatchingPins(hostname)

        if (matchingPins.isEmpty()) {
            println("CertificatePinner: No pins found for host")
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return
        }

        if (challenge.protectionSpace.authenticationMethod !=
            NSURLAuthenticationMethodServerTrust
        ) {
            println("CertificatePinner: Authentication method not suitable for pinning")
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return
        }

        val trust = challenge.protectionSpace.serverTrust
        if (trust == null) {
            println("CertificatePinner: Server trust is not available")
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
            return
        }

        if (validateTrust) {
            val hostCFString = CFStringCreateWithCString(null, hostname, kCFStringEncodingUTF8)
            hostCFString?.use {
                SecPolicyCreateSSL(true, hostCFString)?.use { policy ->
                    SecTrustSetPolicies(trust, policy)
                }
            }
            if (!trust.trustIsValid()) {
                println("CertificatePinner: Server trust is invalid")
                completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                return
            }
        }

        val certCount = SecTrustGetCertificateCount(trust)
        val certificates = (0 until certCount).mapNotNull { index ->
            SecTrustGetCertificateAtIndex(trust, index)
        }

        if (certificates.size != certCount.toInt()) {
            println("CertificatePinner: Unknown certificates")
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
            return
        }

        val result = hasOnePinnedCertificate(certificates)
        if (result) {
            completionHandler(NSURLSessionAuthChallengeUseCredential, challenge.proposedCredential)
        } else {
            val message = buildErrorMessage(certificates, hostname)
            println(message)
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
        }
    }

    /**
     * Confirms that at least one of the certificates is pinned
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun hasOnePinnedCertificate(
        certificates: List<SecCertificateRef>
    ): Boolean = certificates.any { certificate ->
        val publicKey = certificate.getPublicKeyBytes() ?: return@any false
        // Lazily compute the hashes for each public key.
        var sha1: String? = null
        var sha256: String? = null

        pinnedCertificates.any { pin ->
            when (pin.hashAlgorithm) {
                LegacyCertificatesInfo.HASH_ALGORITHM_SHA_256 -> {
                    if (sha256 == null) {
                        sha256 = publicKey.toSha256String()
                    }

                    pin.hash == sha256
                }
                LegacyCertificatesInfo.HASH_ALGORITHM_SHA_1 -> {
                    if (sha1 == null) {
                        sha1 = publicKey.toSha1String()
                    }

                    pin.hash == sha1
                }
                else -> {
                    println("CertificatePinner: Unsupported hashAlgorithm: ${pin.hashAlgorithm}")
                    false
                }
            }
        }
    }

    /**
     * Build an error string to display
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun buildErrorMessage(
        certificates: List<SecCertificateRef>,
        hostname: String
    ): String = buildString {
        append("Certificate pinning failure!")
        append("\n  Peer certificate chain:")
        for (certificate in certificates) {
            append("\n    ")
            val publicKeyStr = certificate.getPublicKeyBytes()?.toSha256String()
            append("${LegacyCertificatesInfo.HASH_ALGORITHM_SHA_256}$publicKeyStr")
            append(": ")
            val summaryRef = SecCertificateCopySubjectSummary(certificate)
            val summary = CFBridgingRelease(summaryRef) as NSString
            append("$summary")
        }
        append("\n  Pinned certificates for ")
        append(hostname)
        append(":")
        for (pin in pinnedCertificates) {
            append("\n    ")
            append(pin)
        }
    }

    /**
     * Returns list of matching certificates' pins for the hostname. Returns an empty list if the
     * hostname does not have pinned certificates.
     */
    internal fun findMatchingPins(hostname: String): List<LegacyPinnedCertificate> {
        var result: List<LegacyPinnedCertificate> = emptyList()
        for (pin in pinnedCertificates) {
            if (pin.matches(hostname)) {
                if (result.isEmpty()) result = mutableListOf()
                (result as MutableList<LegacyPinnedCertificate>).add(pin)
            }
        }
        return result
    }

    /**
     * Evaluates trust for the specified certificate and policies.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun SecTrustRef.trustIsValid(): Boolean {
        var isValid = false

        val version = cValue<NSOperatingSystemVersion> {
            majorVersion = 12
            minorVersion = 0
            patchVersion = 0
        }
        if (NSProcessInfo().isOperatingSystemAtLeastVersion(version)) {
            // https://developer.apple.com/documentation/security/2980705-sectrustevaluatewitherror
            isValid = SecTrustEvaluateWithError(this, null)
        } else {
            // https://developer.apple.com/documentation/security/1394363-sectrustevaluate
            memScoped {
                val result = alloc<SecTrustResultTypeVar>()
                result.value = kSecTrustResultInvalid
                val status = SecTrustEvaluate(this@trustIsValid, result.ptr)
                if (status == errSecSuccess) {
                    isValid = result.value == kSecTrustResultUnspecified ||
                        result.value == kSecTrustResultProceed
                }
            }
        }

        return isValid
    }

    /**
     * Gets the public key from the SecCertificate
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun SecCertificateRef.getPublicKeyBytes(): ByteArray? {
        val publicKeyRef = SecCertificateCopyKey(this) ?: return null

        return publicKeyRef.use {
            val publicKeyAttributes = SecKeyCopyAttributes(publicKeyRef)
            val publicKeyTypePointer = CFDictionaryGetValue(publicKeyAttributes, kSecAttrKeyType)
            val publicKeyType = CFBridgingRelease(publicKeyTypePointer) as NSString
            val publicKeySizePointer = CFDictionaryGetValue(publicKeyAttributes, kSecAttrKeySizeInBits)
            val publicKeySize = CFBridgingRelease(publicKeySizePointer) as NSNumber

            CFBridgingRelease(publicKeyAttributes)

            if (!checkValidKeyType(publicKeyType, publicKeySize)) {
                println("CertificatePinner: Public Key not supported type or size")
                return null
            }

            val publicKeyDataRef = SecKeyCopyExternalRepresentation(publicKeyRef, null)
            val publicKeyData = CFBridgingRelease(publicKeyDataRef) as NSData
            val publicKeyBytes = publicKeyData.toByteArray()
            val headerInts = getAsn1HeaderBytes(publicKeyType, publicKeySize)

            val header = headerInts.foldIndexed(ByteArray(headerInts.size)) { i, a, v ->
                a[i] = v.toByte()
                a
            }
            header + publicKeyBytes
        }
    }

    /**
     * Checks that we support the key type and size
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun checkValidKeyType(publicKeyType: NSString, publicKeySize: NSNumber): Boolean {
        val keyTypeRSA = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val keyTypeECSECPrimeRandom = CFBridgingRelease(kSecAttrKeyTypeECSECPrimeRandom) as NSString

        val size: Int = publicKeySize.intValue.toInt()
        val keys = when (publicKeyType) {
            keyTypeRSA -> LegacyCertificatesInfo.rsa
            keyTypeECSECPrimeRandom -> LegacyCertificatesInfo.ecdsa
            else -> return false
        }

        return keys.containsKey(size)
    }

    /**
     * Get the [IntArray] of Asn1 headers needed to prepend to the public key to create the
     * encoding [ASN1Header](https://docs.oracle.com/middleware/11119/opss/SCRPJ/oracle/security/crypto/asn1/ASN1Header.html)
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun getAsn1HeaderBytes(publicKeyType: NSString, publicKeySize: NSNumber): IntArray {
        val keyTypeRSA = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val keyTypeECSECPrimeRandom = CFBridgingRelease(kSecAttrKeyTypeECSECPrimeRandom) as NSString

        val size: Int = publicKeySize.intValue.toInt()
        val keys = when (publicKeyType) {
            keyTypeRSA -> LegacyCertificatesInfo.rsa
            keyTypeECSECPrimeRandom -> LegacyCertificatesInfo.ecdsa
            else -> return intArrayOf()
        }

        return keys[size] ?: intArrayOf()
    }

    /**
     * Converts a [ByteArray] into sha256 base 64 encoded string
     */
    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalForeignApi::class)
    private fun ByteArray.toSha256String(): String {
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)

        usePinned { inputPinned ->
            digest.usePinned { digestPinned ->
                CC_SHA256(inputPinned.addressOf(0), this.size.convert(), digestPinned.addressOf(0))
            }
        }

        return digest.toByteArray().toNSData().base64EncodedStringWithOptions(0u)
    }

    /**
     * Converts a [ByteArray] into sha1 base 64 encoded string
     */
    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalForeignApi::class)
    private fun ByteArray.toSha1String(): String {
        val digest = UByteArray(CC_SHA1_DIGEST_LENGTH)

        usePinned { inputPinned ->
            digest.usePinned { digestPinned ->
                CC_SHA1(inputPinned.addressOf(0), this.size.convert(), digestPinned.addressOf(0))
            }
        }
        return digest.toByteArray().toNSData().base64EncodedStringWithOptions(0u)
    }

    /**
     * Builds a configured [LegacyCertificatePinner].
     */
    public data class Builder(
        private val pinnedCertificates: MutableList<LegacyPinnedCertificate> = mutableListOf(),
        private var validateTrust: Boolean = true
    ) {
        /**
         * Pins certificates for `pattern`.
         *
         * @param pattern lower-case host name or wildcard pattern such as `*.example.com`.
         * @param pins SHA-256 or SHA-1 hashes. Each pin is a hash of a certificate's
         * Subject Public Key Info, base64-encoded and prefixed with either `sha256/` or `sha1/`.
         * @return The [Builder] so calls can be chained
         */
        public fun add(pattern: String, vararg pins: String): Builder = apply {
            pins.forEach { pin ->
                this.pinnedCertificates.add(
                    LegacyPinnedCertificate.new(
                        pattern,
                        pin
                    )
                )
            }
        }

        /**
         * Whether to valid the trust of the server
         * https://developer.apple.com/documentation/security/2980705-sectrustevaluatewitherror
         * @param validateTrust
         * @return The [Builder] so calls can be chained
         */
        public fun validateTrust(validateTrust: Boolean): Builder = apply {
            this.validateTrust = validateTrust
        }

        /**
         * Build into a [LegacyCertificatePinner]
         * @return [LegacyCertificatePinner]
         */
        public fun build(): LegacyCertificatePinner = LegacyCertificatePinner(
            pinnedCertificates.toSet(),
            validateTrust
        )
    }
}
