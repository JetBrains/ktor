/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.certificates.*
import io.ktor.network.tls.extensions.*
import io.ktor.util.*
import java.io.*
import java.security.*
import java.security.cert.Certificate
import java.util.*
import kotlin.test.*

internal class TLSConfigBuilderTest {
    /**
     * Implemented a new KeyStoreSpi, because the JavaKeyStore does not allow setting null as password.
     *
     * Some other KeyStoreSpis, like the KeyStoreSpi needs null as passwords
     * because they act as a middleware and en-/decrypt the keys with other passwords, like a smartcard.
     *
     * This NON-ENCRYPTED in-memory KeyStore should be used for testing only!
     */
    private class InMemoryKeyStoreSPI : KeyStoreSpi() {
        sealed class Entry(val date: Date = Date()) {
            abstract var certificates: Array<Certificate>

            class KeyEntry(val password: CharArray?, val key: Key, override var certificates: Array<Certificate>) :
                Entry()

            class CertificateEntry(override var certificates: Array<Certificate>) : Entry()
        }

        private val certificates: MutableMap<String, Entry> = mutableMapOf()

        override fun engineGetKey(alias: String, password: CharArray?): Key? =
            (certificates[alias] as? Entry.KeyEntry?)?.takeIf { entry ->
                entry.password.contentEquals(password)
            }?.key

        override fun engineGetCertificateChain(alias: String): Array<Certificate>? =
            certificates[alias]?.certificates

        override fun engineGetCertificate(alias: String): Certificate? =
            engineGetCertificateChain(alias)?.firstOrNull()

        override fun engineGetCreationDate(alias: String): Date? {
            return certificates[alias]?.date
        }

        override fun engineSetKeyEntry(
            alias: String,
            key: Key,
            password: CharArray?,
            chain: Array<Certificate>
        ) {
            certificates[alias] = Entry.KeyEntry(password, key, chain)
        }

        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>) {
            error("Not supported")
        }

        override fun engineSetCertificateEntry(alias: String, cert: Certificate) {
            certificates[alias]?.certificates = arrayOf(cert)
        }

        override fun engineDeleteEntry(alias: String) {
            certificates.remove(alias)
        }

        override fun engineAliases() = certificates.keys.iterator().enumeration

        private val Iterator<String>.enumeration: Enumeration<String>
            get() = object : Enumeration<String> {
                override fun hasMoreElements() = this@enumeration.hasNext()
                override fun nextElement() = this@enumeration.next()
            }

        override fun engineContainsAlias(alias: String) = certificates.containsKey(alias)

        override fun engineSize() = certificates.size

        override fun engineIsKeyEntry(alias: String): Boolean {
            return certificates[alias] is Entry.KeyEntry
        }

        override fun engineIsCertificateEntry(alias: String): Boolean {
            return certificates[alias] is Entry.CertificateEntry
        }

        override fun engineGetCertificateAlias(cert: Certificate): String {
            return certificates.filterValues { entry ->
                entry.certificates.first() == cert
            }.keys.first()
        }

        override fun engineStore(stream: OutputStream?, password: CharArray?) {
            error("Not supported")
        }

        override fun engineLoad(stream: InputStream?, password: CharArray?) {
            check(stream == null)
        }
    }

    private val keyStore = buildKeyStore {
        certificate("first") {
            hash = HashAlgorithm.SHA256
            sign = SignatureAlgorithm.RSA
            password = ""
        }
        certificate("second") {
            hash = HashAlgorithm.SHA256
            sign = SignatureAlgorithm.RSA
            password = ""
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun useNullAsPassword() {
        val customProvider = object : Provider("InMemorySPI", 0.0, "") {
            override fun getService(type: String?, algorithm: String?) =
                object : Service(this, this.name, "", InMemoryKeyStoreSPI::class.simpleName, emptyList(), mapOf()) {
                    override fun newInstance(constructorParameter: Any?) = InMemoryKeyStoreSPI()
                }
        }

        val ks = generateCertificate(File.createTempFile("tmp", "jks"))
        val key = ks.getKey("myKey", "changeit".toCharArray())
        val cert = ks.getCertificate("myKey")

        val keyStore = KeyStore.getInstance("InMemorySPI", customProvider).apply {
            load(null, null)
            setKeyEntry("myKey", key, null, arrayOf(cert))
        }
        val tlsConfig = TLSConfigBuilder().apply {
            addKeyStore(keyStore, null)
        }
        assertEquals(1, tlsConfig.certificates.size)
    }

    @Test
    fun useAllCertificates() {
        val config = TLSConfigBuilder().apply {
            addKeyStore(keyStore, "".toCharArray())
        }
        assertEquals(2, config.certificates.size)
    }

    @Test
    fun specificAliasInKeyStore() {
        val config = TLSConfigBuilder().apply {
            addKeyStore(keyStore, "".toCharArray(), "first")
        }
        assertEquals(1, config.certificates.size)
    }

    @Test
    fun aliasNotFound() {
        assertFailsWith<IllegalStateException> {
            TLSConfigBuilder().apply {
                addKeyStore(keyStore, "".toCharArray() as CharArray?, "notFound")
            }
        }
    }
}
