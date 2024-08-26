/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.html

import kotlinx.html.*
import kotlin.test.*

class PlaceholderListTest {

    @Test
    fun testNewPlaceholderListIsEmpty() {
        val placeholderList = PlaceholderList<FlowContent, FlowContent>()
        assertTrue(placeholderList.isEmpty())
    }

    @Test
    fun testPlaceholderListWithContentIsNotEmpty() {
        val placeholderList = PlaceholderList<FlowContent, FlowContent>()
        placeholderList.invoke()
        assertTrue(placeholderList.isNotEmpty())
    }

    @Test
    fun testNewPlaceholderListHasZeroSize() {
        val placeholderList = PlaceholderList<FlowContent, FlowContent>()
        assertEquals(0, placeholderList.size)
    }

    @Test
    fun testPlaceholderListSizeIsEqualToNumberOfPlaceholdersInIt() {
        val placeholderList = PlaceholderList<FlowContent, FlowContent>()
        val count = 10
        for (i in 0 until count) {
            placeholderList.invoke()
        }

        assertEquals(count, placeholderList.size)
    }
}
