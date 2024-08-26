/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.ldap

import io.ktor.server.auth.*
import java.util.*
import javax.naming.*
import javax.naming.directory.*

/**
 * Provides the ability to authenticates an LDAP user.
 * This function accepts a credential and validates it against a specified LDAP server.
 *
 * To learn more about LDAP authentication in Ktor, see [LDAP](https://ktor.io/docs/ldap.html).
 */
public fun <K : Any, P : Any> ldapAuthenticate(
    credential: K,
    ldapServerURL: String,
    ldapEnvironmentBuilder: (MutableMap<String, Any?>) -> Unit = {},
    doVerify: InitialDirContext.(K) -> P?
): P? {
    return try {
        val root = ldapLogin(ldapServerURL, ldapEnvironmentBuilder)
        try {
            root.doVerify(credential)
        } finally {
            root.close()
        }
    } catch (ne: NamingException) {
        null
    }
}

/**
 * Provides the ability to authenticates an LDAP user.
 * This function accepts [UserPasswordCredential] and validates it against a specified LDAP server.
 *
 * To learn more about LDAP authentication in Ktor, see [LDAP](https://ktor.io/docs/ldap.html).
 */
public fun <P : Any> ldapAuthenticate(
    credential: UserPasswordCredential,
    ldapServerURL: String,
    userDNFormat: String,
    validate: InitialDirContext.(UserPasswordCredential) -> P?
): P? {
    val configurator: (MutableMap<String, Any?>) -> Unit = { env ->
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = userDNFormat.format(ldapEscape(credential.name))
        env[Context.SECURITY_CREDENTIALS] = credential.password
    }

    return ldapAuthenticate(credential, ldapServerURL, configurator, validate)
}

/**
 * Provides the ability to authenticates an LDAP user.
 * This function accepts [UserPasswordCredential] and validates it against a specified LDAP server.
 *
 * To learn more about LDAP authentication in Ktor, see [LDAP](https://ktor.io/docs/ldap.html).
 */
public fun ldapAuthenticate(
    credential: UserPasswordCredential,
    ldapServerURL: String,
    userDNFormat: String
): UserIdPrincipal? {
    return ldapAuthenticate(credential, ldapServerURL, userDNFormat) { UserIdPrincipal(it.name) }
}

private fun ldapLogin(ldapURL: String, ldapEnvironmentBuilder: (MutableMap<String, Any?>) -> Unit): InitialDirContext {
    val env = Hashtable<String, Any?>()
    env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
    env[Context.PROVIDER_URL] = ldapURL

    ldapEnvironmentBuilder(env)

    return InitialDirContext(env)
}

internal fun ldapEscape(string: String): String {
    for (index in 0..string.lastIndex) {
        val character = string[index]
        if (character.shouldEscape()) {
            return ldapEscapeImpl(string, index)
        }
    }

    return string
}

private fun ldapEscapeImpl(string: String, firstIndex: Int): String = buildString {
    var lastIndex = 0
    for (index in firstIndex..string.lastIndex) {
        val character = string[index]
        if (character.shouldEscape()) {
            append(string, lastIndex, index)
            if (character in ESCAPE_CHARACTERS) {
                append('\\')
                append(character)
            } else {
                character.toString().toByteArray().let { encoded ->
                    for (element in encoded) {
                        val unsignedValue = element.toInt() and 0xff
                        append('\\')
                        append(unsignedValue.toString(16).padStart(2, '0'))
                    }
                }
            }

            lastIndex = index + 1
        }
    }

    append(string, lastIndex, string.length)
}

private val ESCAPE_CHARACTERS = charArrayOf(' ', '"', '#', '+', ',', ';', '<', '=', '>', '\\')

private fun Char.shouldEscape(): Boolean = this.code.let { codepoint ->
    when (codepoint) {
        in 0x3f..0x7e -> codepoint == 0x5c // the only forbidden character is backslash
        in 0x2d..0x3a -> false // minus, point, slash (allowed), digits + colon :
        in 0x24..0x2a -> false // $%&'()*
        0x21 -> false // exclamation
        else -> true
    }
}
