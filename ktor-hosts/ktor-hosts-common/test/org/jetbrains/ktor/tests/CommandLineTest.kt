package org.jetbrains.ktor.tests

import org.jetbrains.ktor.host.*
import org.junit.*
import org.junit.rules.*
import org.junit.runner.*
import org.junit.runners.model.*
import java.io.*
import java.net.*
import java.util.*
import kotlin.test.*

class CommandLineTest {

    @get:Rule
    var classLoader = IsolatedClassLoaderRule()

    @Test
    fun testEmpty() {
        commandLineConfig(emptyArray())
    }

    @Test
    fun testChangePort() {
        assertEquals(13698, commandLineConfig(arrayOf("-port=13698")).first.connectors.single().port)
    }

    @Test
    fun testAmendConfig() {
        assertEquals(13698, commandLineConfig(arrayOf("-P:ktor.deployment.port=13698")).first.connectors.single().port)
    }

    @Test
    fun testChangeHost() {
        assertEquals("test-server", commandLineConfig(arrayOf("-host=test-server")).first.connectors.single().host)
    }

    @Test
    fun testSingleArgument() {
        commandLineConfig(arrayOf("-it-should-be-no-effect"))
    }

    @Test
    fun testJar() {
        val jar = findContainingZipFile(CommandLineTest::class.java.classLoader.getResources("java/util/ArrayList.class").nextElement().toURI())
        val urlClassLoader = commandLineConfig(arrayOf("-jar=${jar.absolutePath}")).second.classLoader as URLClassLoader
        assertEquals(jar.toURI(), urlClassLoader.urLs.single().toURI())
    }

    tailrec
    private fun findContainingZipFile(uri: URI): File {
        if (uri.scheme == "file") {
            return File(uri.path.substringBefore("!"))
        } else {
            return findContainingZipFile(URI(uri.rawSchemeSpecificPart))
        }
    }

    class IsolatedClassLoaderRule : TestRule {
        override fun apply(s: Statement, d: Description): Statement {
            return object : Statement() {
                override fun evaluate() {
                    withIsolatedClassLoader {
                        s.evaluate()
                    }
                }
            }
        }

        private fun withIsolatedClassLoader(block: (ClassLoader) -> Unit) {
            val classLoader = IsolatedResourcesClassLoader(
                    File("ktor-hosts/ktor-hosts-common/test-resources").absoluteFile,
                    block::class.java.classLoader)

            val oldClassLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = classLoader
            try {
                block(classLoader)
            } finally {
                Thread.currentThread().contextClassLoader = oldClassLoader
            }
        }
    }

    private class IsolatedResourcesClassLoader(val dir: File, parent: ClassLoader) : ClassLoader(parent) {
        override fun getResources(name: String): Enumeration<URL> {
            val lookup = File(dir, name)
            if (lookup.isFile) return listOf(lookup.absoluteFile.toURI().toURL()).let { Collections.enumeration<URL>(it) }
            return parent.getResources(name)
        }

        override fun getResource(name: String): URL? {
            val lookup = File(dir, name)
            if (lookup.isFile) return lookup.absoluteFile.toURI().toURL()
            return parent.getResource(name)
        }

        override fun getResourceAsStream(name: String): InputStream? {
            return getResource(name)?.openStream()
        }
    }
}