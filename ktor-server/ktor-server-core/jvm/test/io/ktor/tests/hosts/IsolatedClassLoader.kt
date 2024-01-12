/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.hosts

import org.junit.jupiter.api.extension.*
import java.io.*
import java.net.*
import java.util.*

class UseIsolatedClassLoader : BeforeAllCallback, AfterAllCallback {

    private var oldClassLoader: ClassLoader? = null

    override fun beforeAll(context: ExtensionContext?) {
        oldClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = IsolatedResourcesClassLoader(
            File("ktor-server/ktor-server-core/test-resources").absoluteFile,
            oldClassLoader!!
        )
    }

    override fun afterAll(context: ExtensionContext?) {
        Thread.currentThread().contextClassLoader = oldClassLoader!!
    }
}

private class IsolatedResourcesClassLoader(val dir: File, parent: ClassLoader) : ClassLoader(parent) {
    override fun getResources(name: String): Enumeration<URL> {
        val lookup = File(dir, name)
        if (lookup.isFile) {
            return listOf(lookup.absoluteFile.toURI().toURL()).let { Collections.enumeration<URL>(it) }
        }
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
