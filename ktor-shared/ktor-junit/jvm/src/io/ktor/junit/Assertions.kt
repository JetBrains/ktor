/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.junit

/**
 * Convenience function for asserting on all elements of a collection.
 */
fun <T> assertAll(collection: Iterable<T>, message: String? = null, predicate: (T) -> Boolean) {
    org.junit.jupiter.api.assertAll(
        collection.map { item ->
            {
                org.junit.jupiter.api.Assertions.assertTrue(predicate(item), message)
            }
        }
    )
}

/**
 * Convenience function for asserting on all elements of a collection.
 */
fun <T> assertAll(collection: Iterable<T>, assertion: (T) -> Unit) {
    org.junit.jupiter.api.assertAll(
        collection.map { item ->
            {
                assertion(item)
            }
        }
    )
}
