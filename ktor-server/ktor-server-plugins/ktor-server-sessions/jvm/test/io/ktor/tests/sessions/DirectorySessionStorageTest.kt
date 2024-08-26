/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.sessions

import io.ktor.server.sessions.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.file.*
import kotlin.test.*

class DirectorySessionStorageTest {
    private val dir = Files.createTempDirectory("ktor-tests-").toFile()
    private val storage = directorySessionStorage(dir, false)

    @AfterTest
    fun tearDown() {
        (storage as Closeable).close()
        assertTrue { dir.deleteRecursively() }
    }

    @Test
    fun testSetup() {
    }

    @Test
    fun testMissingSession(): Unit = runBlocking {
        assertFailsWith<NoSuchElementException> {
            storage.read("id0")
        }
    }

    @Test
    fun testSaveSimple(): Unit = runBlocking {
        storage.write("id1", "test1")
        assertEquals("test1", storage.read("id1"))
    }

    @Test
    fun testSaveTwice(): Unit = runBlocking {
        storage.write("id1", "test1 with tail")
        storage.write("id1", "test2")
        assertEquals("test2", storage.read("id1"))
    }

    @Test
    fun testInvalidate(): Unit = runBlocking {
        testSaveSimple()
        storage.invalidate("id1")
        assertFailsWith(NoSuchElementException::class) {
            runBlocking {
                storage.read("id1")
            }
        }
    }
}
