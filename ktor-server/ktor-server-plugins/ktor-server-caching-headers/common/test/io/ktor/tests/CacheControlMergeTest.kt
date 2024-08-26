/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests

import io.ktor.http.*
import io.ktor.http.CacheControl.*
import io.ktor.server.plugins.cachingheaders.*
import kotlin.test.*

class CacheControlMergeTest {
    @Test
    fun testMergeEmpty() {
        assertEquals(emptyList(), merge())
    }

    @Test
    fun testMergeSingleEntry() {
        assertEquals(listOf(NoStore(null)), merge(NoStore(null)))
        assertEquals(listOf(NoStore(Visibility.Public)), merge(NoStore(Visibility.Public)))
        assertEquals(listOf(NoStore(Visibility.Private)), merge(NoStore(Visibility.Private)))
        assertEquals(listOf(NoCache(null)), merge(NoCache(null)))
        assertEquals(listOf(NoCache(Visibility.Public)), merge(NoCache(Visibility.Public)))
        assertEquals(listOf(NoCache(Visibility.Private)), merge(NoCache(Visibility.Private)))
        assertEquals(listOf(MaxAge(1)), merge(MaxAge(1)))
        assertEquals(listOf(MaxAge(2)), merge(MaxAge(2)))
        assertEquals(
            listOf(MaxAge(3, visibility = Visibility.Private)),
            merge(MaxAge(3, visibility = Visibility.Private))
        )
    }

    @Test
    fun testOrderNoMaxAge() {
        assertEquals(listOf(NoCache(null), NoStore(null)), merge(NoCache(null), NoStore(null)))
        assertEquals(listOf(NoCache(null), NoStore(null)), merge(NoStore(null), NoCache(null)))
    }

    @Test
    fun testMergeNoMaxAge() {
        assertEquals(listOf(NoCache(null), NoStore(null)), merge(NoCache(null), NoStore(null)))
        assertEquals(listOf(NoCache(null)), merge(NoCache(null), NoCache(null)))
        assertEquals(listOf(NoCache(Visibility.Private)), merge(NoCache(null), NoCache(Visibility.Private)))
        assertEquals(listOf(NoCache(Visibility.Private)), merge(NoCache(Visibility.Private), NoCache(null)))
    }

    @Test
    fun testTripleMergeNoMaxAge() {
        assertEquals(
            listOf(NoCache(Visibility.Private)),
            merge(
                NoCache(Visibility.Private),
                NoCache(null),
                NoCache(Visibility.Public),
            )
        )
        assertEquals(
            listOf(NoCache(Visibility.Public)),
            merge(
                NoCache(Visibility.Public),
                NoCache(null),
                NoCache(Visibility.Public),
            )
        )
    }

    @Test
    fun testPrivateMergeNoMaxAge() {
        assertEquals(
            listOf(NoCache(Visibility.Private), NoStore(null)),
            merge(NoCache(Visibility.Private), NoStore(null))
        )
        assertEquals(
            listOf(NoCache(Visibility.Private), NoStore(null)),
            merge(NoCache(Visibility.Private), NoStore(Visibility.Private))
        )
        assertEquals(
            listOf(NoCache(Visibility.Private), NoStore(null)),
            merge(NoCache(Visibility.Private), NoStore(Visibility.Public))
        )
        assertEquals(
            listOf(NoCache(Visibility.Private), NoStore(null)),
            merge(NoCache(Visibility.Public), NoStore(Visibility.Private))
        )
    }

    @Test
    fun testPublicMergeNoMaxAge() {
        assertEquals(
            listOf(NoCache(Visibility.Public), NoStore(null)),
            merge(NoCache(Visibility.Public), NoStore(null))
        )
        assertEquals(
            listOf(NoCache(Visibility.Public), NoStore(null)),
            merge(NoCache(null), NoStore(Visibility.Public))
        )
        assertEquals(
            listOf(NoCache(Visibility.Public), NoStore(null)),
            merge(NoCache(Visibility.Public), NoStore(Visibility.Public))
        )
    }

    @Test
    fun testSimpleMaxAgeMerge() {
        assertEquals(
            listOf(MaxAge(1, 1, true, true, Visibility.Private)),
            merge(
                MaxAge(1, 2, true, false, null),
                MaxAge(2, 1, false, true, Visibility.Public),
                MaxAge(20, 10, false, false, Visibility.Private)
            )
        )
    }

    @Test
    fun testAgeMergeWithNoCache() {
        assertEquals(
            listOf(
                NoCache(null),
                MaxAge(1, 2, true, false, Visibility.Private)
            ),
            merge(
                MaxAge(1, 2, true, false, null),
                NoCache(Visibility.Private)
            )
        )

        assertEquals(
            listOf(
                NoCache(null),
                MaxAge(1, 2, true, false, Visibility.Private)
            ),
            merge(
                MaxAge(1, 2, true, false, Visibility.Public),
                NoCache(Visibility.Private)
            )
        )

        assertEquals(
            listOf(
                NoCache(null),
                MaxAge(1, 2, true, false, Visibility.Public)
            ),
            merge(
                MaxAge(1, 2, true, false, Visibility.Public),
                NoCache(null)
            )
        )
    }

    @Test
    fun testAgeMergeWithNoStore() {
        assertEquals(
            listOf(
                NoStore(null),
                MaxAge(1, 2, true, false, Visibility.Private)
            ),
            merge(
                MaxAge(1, 2, true, false, null),
                NoStore(Visibility.Private)
            )
        )

        assertEquals(
            listOf(
                NoStore(null),
                MaxAge(1, 2, true, false, Visibility.Private)
            ),
            merge(
                MaxAge(1, 2, true, false, Visibility.Public),
                NoStore(Visibility.Private)
            )
        )

        assertEquals(
            listOf(
                NoStore(null),
                MaxAge(1, 2, true, false, Visibility.Public)
            ),
            merge(
                MaxAge(1, 2, true, false, Visibility.Public),
                NoStore(null)
            )
        )
    }

    @Test
    fun testAgeMergeWithNoCacheAndNoStore() {
        assertEquals(
            listOf(
                NoCache(null),
                NoStore(null),
                MaxAge(1, 2, true, false, Visibility.Private)
            ),
            merge(
                MaxAge(1, 2, true, false, null),
                NoStore(Visibility.Private),
                NoCache(Visibility.Private)
            )
        )

        assertEquals(
            listOf(
                NoCache(null),
                NoStore(null),
                MaxAge(1, 2, true, false, null)
            ),
            merge(
                MaxAge(1, 2, true, false, null),
                NoStore(null),
                NoCache(null)
            )
        )

        assertEquals(
            listOf(
                NoCache(null),
                NoStore(null),
                MaxAge(1, 2, true, false, Visibility.Public)
            ),
            merge(
                MaxAge(1, 2, true, false, null),
                NoStore(Visibility.Public),
                NoCache(Visibility.Public)
            )
        )

        assertEquals(
            listOf(
                NoCache(null),
                NoStore(null),
                MaxAge(1, 2, true, false, Visibility.Private)
            ),
            merge(
                MaxAge(1, 2, true, false, Visibility.Private),
                NoStore(null),
                NoCache(null)
            )
        )

        assertEquals(
            listOf(
                NoCache(null),
                NoStore(null),
                MaxAge(1, 2, true, false, Visibility.Private)
            ),
            merge(
                MaxAge(1, 2, true, false, Visibility.Public),
                NoStore(null),
                NoCache(null),
                NoCache(Visibility.Public),
                NoStore(Visibility.Private),
            )
        )
    }

    private fun merge(vararg cacheControl: CacheControl): List<CacheControl> {
        return cacheControl.asList().mergeCacheControlDirectives()
    }
}
