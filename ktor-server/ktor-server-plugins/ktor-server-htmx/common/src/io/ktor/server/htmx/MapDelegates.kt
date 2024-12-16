/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.htmx

import kotlin.reflect.KProperty

internal interface StringMap {
    operator fun set(key: String, value: String)
    operator fun get(key: String): String?
    fun remove(key: String): String?
}

internal interface StringMapDelegate : StringMap {
    val map: MutableMap<String, String>

    override fun set(key: String, value: String): Unit = map.set(key, value)
    override fun get(key: String): String? = map[key]
    override fun remove(key: String): String? = map.remove(key)
}

/**
 * Simplifies property access delegation for HxAttributes when setting attribute values from a string constant.
 */
internal operator fun String.getValue(thisRef: StringMap, property: KProperty<*>): String? =
    thisRef[this]

/**
 * Simplifies property assignment delegation for HxAttributes when setting attribute values from a string constant.
 */
internal operator fun String.setValue(thisRef: StringMap, property: KProperty<*>, value: String?) {
    if (value == null) {
        thisRef.remove(this)
    } else {
        thisRef[this] = value
    }
}
