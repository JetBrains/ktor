package io.ktor.network.tls

import kotlin.test.*

class MatchHostnameTest {
    @Test
    fun testMatchExact() {
        assertTrue(matchHostnameWithCertificate("www.example.com", "www.example.com"))
        assertTrue(matchHostnameWithCertificate("example.com", "example.com"))
    }

    @Test
    fun testMatchWildcardCertificate() {
        assertTrue(matchHostnameWithCertificate("www.example.com", "*.example.com"))
        assertFalse(matchHostnameWithCertificate("www.example.com", "*.com"))
        assertFalse(matchHostnameWithCertificate("www.example.com", "*"))
    }

    @Test
    fun testEmptyCertificate() {
        assertFalse(matchHostnameWithCertificate("www.example.com", ""))
    }

    @Test
    fun testInvalidNameInCertificate() {
        assertFalse(matchHostnameWithCertificate("www.example.com", "www.example.org"))
    }

    @Test
    fun testMatchesAbsolute() {
        assertTrue(matchHostnameWithCertificate("example.com", "example.com"))
        assertTrue(matchHostnameWithCertificate("example.com", "example.com."))
        assertTrue(matchHostnameWithCertificate("example.com.", "example.com"))
        assertTrue(matchHostnameWithCertificate("example.com.", "example.com."))
    }

    @Test
    fun testMatchingIgnoreCase() {
        assertTrue(matchHostnameWithCertificate("example.com", "EXAMPLE.com"))
        assertTrue(matchHostnameWithCertificate("EXAMPLE.com", "example.com"))
    }

    @Test
    fun testInvalidWildcard() {
        assertFalse(matchHostnameWithCertificate("www.example.com", "*.*.com"))
        assertFalse(matchHostnameWithCertificate("www.example.com", "*.*.*"))
        assertFalse(matchHostnameWithCertificate("www.example.com", "*.*"))
        assertFalse(matchHostnameWithCertificate("www.example.com", "*."))
    }

    @Test
    fun testNestedWildcard() {
        assertTrue(matchHostnameWithCertificate("www.example.com", "*.example.com"))
        assertTrue(matchHostnameWithCertificate("www.sub.example.com", "*.*.example.com"))
        assertTrue(matchHostnameWithCertificate("www.sub.sub.example.com", "*.*.*.example.com"))
        assertFalse(matchHostnameWithCertificate("www.sub.sub.example.com", "*.sub.example.com"))
    }

    @Test
    fun testDifferentLength() {
        assertFalse(matchHostnameWithCertificate("www.example.com", "www.sub.example.com"))
        assertFalse(matchHostnameWithCertificate("www.sub.example.com", "www.example.com"))
    }

    @Test
    fun testWildcardInWrongPosition() {
        assertFalse(matchHostnameWithCertificate("www.sub.example.com", "www.*.example.com"))
        assertFalse(matchHostnameWithCertificate("www.sub.example.com", "www.sub.*.com"))
    }
}
