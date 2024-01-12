/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

/**
 * A user's principal identified by [name].
 *
 * @see [Authentication]
 * @property name of user
 */
public data class UserIdPrincipal(val name: String) : Principal

/**
 * A user's credentials identified by [name] and [password].
 *
 * @see [Authentication]
 * @property name
 * @property password
 */
public data class UserPasswordCredential(val name: String, val password: String) : Credential

public data class BearerTokenCredential(val token: String) : Credential

/**
 * An in-memory table that keeps usernames and password hashes.
 * This allows you not to compromise user passwords if your data source is leaked.
 *
 * @see [basic]
 * @see [form]
 * @property digester a hash function to compute password digest
 * @property table of usernames and hashed passwords
 */
public class UserHashedTableAuth(public val digester: (String) -> ByteArray, public val table: Map<String, ByteArray>) {
    init {
        if (table.isEmpty()) {
            // TODO log no users configured
        }
    }

    /**
     * Authenticates a user by [credential] and returns a [UserIdPrincipal] instance if the [credential] pair is valid.
     */
    public fun authenticate(credential: UserPasswordCredential): UserIdPrincipal? {
        val userPasswordHash = table[credential.name]
        if (userPasswordHash != null && digester(credential.password) contentEquals userPasswordHash) {
            return UserIdPrincipal(credential.name)
        }

        return null
    }
}
