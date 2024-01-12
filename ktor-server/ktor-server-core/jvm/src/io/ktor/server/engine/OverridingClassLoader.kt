/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.io.*
import java.net.*
import java.util.*

/**
 * A parent-last classloader that will try the child classloader first and then the parent.
 */
internal class OverridingClassLoader(
    classpath: List<URL>,
    parentClassLoader: ClassLoader?
) : ClassLoader(parentClassLoader), Closeable {
    private val childClassLoader = ChildURLClassLoader(classpath.toTypedArray(), parent)

    @Synchronized
    override fun loadClass(name: String, resolve: Boolean): Class<*> = try {
        // first we try to find a class inside the child classloader
        childClassLoader.findClass(name)
    } catch (e: ClassNotFoundException) {
        // didn't find it, try the parent
        super.loadClass(name, resolve)
    }

    override fun close() {
        childClassLoader.close()
    }

    /**
     * This class delegates (child then parent) for the findClass method for a URLClassLoader.
     * We need this because findClass is protected in URLClassLoader
     */
    private class ChildURLClassLoader(urls: Array<URL>, private val realParent: ClassLoader) :
        URLClassLoader(urls, null) {
        public override fun findClass(name: String): Class<*> {
            val loaded = super.findLoadedClass(name)
            if (loaded != null) {
                return loaded
            }

            try {
                // first try to use the URLClassLoader findClass
                return super.findClass(name)
            } catch (e: ClassNotFoundException) {
                // if that fails, we ask our real parent classloader to load the class (we give up)
                return realParent.loadClass(name)
            }
        }

        // Always delegate to realParent.
        // There is no point in loading resources through this classloader as it will always be a subset of the
        // classpath for the parent. Also they do not need to be reloaded as they are not cached by the classloader
        // unlike classes.
        // TODO: "parent-last classloader" would obviously also be so for resources
        override fun getResources(name: String?): Enumeration<URL> = realParent.getResources(name)
        override fun getResource(name: String?): URL? = realParent.getResource(name)
        override fun getResourceAsStream(name: String?): InputStream? = realParent.getResourceAsStream(name)

        // We cannot delegate these to realParent as it has "protected" visibility.
        // However, as they are protected they should in practice not be called unless this classloader is cast to
        // URLClassLoader. In that event we revert to the default impl as defined in ClassLoader. Seems safe enough.
        override fun findResource(name: String?): URL? = null
        override fun findResources(name: String?): Enumeration<URL> = Collections.emptyEnumeration()
    }
}
