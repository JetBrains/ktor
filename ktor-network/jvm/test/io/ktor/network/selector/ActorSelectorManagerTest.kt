/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.mockk.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import java.io.*
import kotlin.test.*
import kotlin.test.Test

class ActorSelectorManagerTest {
    val manager = ActorSelectorManager(Dispatchers.Default)

    @AfterEach
    fun tearDown() {
        manager.close()
    }

    @Test
    fun testSelectableIsClosed(): Unit = runBlocking {
        val selectable: Selectable = mockk()
        every { selectable.interestedOps } returns SelectInterest.READ.flag
        every { selectable.isClosed } returns true

        assertFailsWith<IOException> {
            manager.select(selectable, SelectInterest.READ)
        }
    }

    @Test
    fun testSelectOnWrongInterest(): Unit = runBlocking {
        val selectable: Selectable = mockk()
        every { selectable.interestedOps } returns SelectInterest.READ.flag
        every { selectable.isClosed } returns false

        assertFailsWith<IllegalStateException> {
            manager.select(selectable, SelectInterest.WRITE)
        }
    }
}
