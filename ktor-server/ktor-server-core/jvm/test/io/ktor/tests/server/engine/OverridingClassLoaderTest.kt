/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.io.*
import java.net.*
import kotlin.reflect.full.*
import kotlin.test.*

class OverridingClassLoaderTest {

    @Test
    @Ignore("Does not work on team city CI for some reason")
    fun childClassloaderDelegatesToParentForResources() {
        val thisClassLoader = javaClass.classLoader
        thisClassLoader.getResourceAsStream("resource.txt").use {
            // Check that it can be found if we load directly with the "parent" class loader
            checkNotNull(it)
        }
        // Ok. Then it should be possible to load it through the child class loader also.

        // Construct absolute classpath for the test source set
        val thisClassFilename = javaClass.simpleName + ".class"
        val classLocation = checkNotNull(javaClass.getResource(thisClassFilename)) {
            "Was not able to locate test class file '$thisClassFilename'"
        }
        check(classLocation.protocol == "file") {
            "Expected class to be located on the file system but was ${classLocation.protocol}"
        }
        val testClassesUrl = run {
            // class location is something like
            // <AbsoluteFilePrefix>/ktor/ktor-server/ktor-server-core/build/classes/atomicfu/jvm/test/io/ktor/server/engine/OverridingClassLoaderTest.class
            val classLocationString = classLocation.toString()
            val expectedTestClassPath = "io/ktor/server/engine/OverridingClassLoaderTest.class"
            check(classLocationString.endsWith(expectedTestClassPath)) {
                "Expected $classLocationString to end with $expectedTestClassPath"
            }
            URL(classLocationString.removeSuffix(expectedTestClassPath))
        }

        val text = OverridingClassLoader(listOf(testClassesUrl), thisClassLoader).use {
            val childLoadedClassClazz = it.loadClass("io.ktor.server.engine.ChildLoadedClass")
            val expectedClassloaderPrefix = "io.ktor.server.engine.OverridingClassLoader\$ChildURLClassLoader"
            // Check it was loaded by the child class loader
            val actualClassLoaderName = childLoadedClassClazz.classLoader.toString()
            check(actualClassLoaderName.startsWith(expectedClassloaderPrefix)) {
                "Was loaded by $actualClassLoaderName. Expected something with prefix $expectedClassloaderPrefix" +
                    " in classpath $testClassesUrl"
            }

            // Great. Attempt to use it to load a resource we know exists
            val resourceStream = childLoadedClassClazz.kotlin.primaryConstructor!!
                .call("resource.txt")
                .let {
                    @Suppress("UNCHECKED_CAST")
                    it as () -> InputStream?
                }
                .invoke()

            resourceStream?.bufferedReader().use { it?.readText() }
        }

        assertEquals("resource example\n", text)
    }
}

/**
 * A class that loads resources as they generally do.
 */
@Suppress("UNUSED")
class ChildLoadedClass(
    private val resourceName: String,
) : () -> InputStream? {

    override fun invoke(): InputStream? {
        return javaClass.classLoader.getResourceAsStream(resourceName)
    }
}
