/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * A map with case-insensitive [String] keys
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.util.CaseInsensitiveMap)
 */
public class CaseInsensitiveMap<Value : Any> : MutableMap<String, Value> {
    private val delegate = mutableMapOf<CaseInsensitiveString, Value>()

    override val size: Int get() = delegate.size

    override fun containsKey(key: String): Boolean = delegate.containsKey(CaseInsensitiveString(key))

    override fun containsValue(value: Value): Boolean = delegate.containsValue(value)

    override fun get(key: String): Value? = delegate[key.caseInsensitive()]

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun clear() {
        delegate.clear()
    }

    override fun put(key: String, value: Value): Value? = delegate.put(key.caseInsensitive(), value)

    override fun putAll(from: Map<out String, Value>) {
        from.forEach { (key, value) -> put(key, value) }
    }

    override fun remove(key: String): Value? = delegate.remove(key.caseInsensitive())

    override val keys: MutableSet<String>
        get() = DelegatingMutableSet(
            delegate.keys,
            { content },
            { caseInsensitive() }
        )

    override val entries: MutableSet<MutableMap.MutableEntry<String, Value>>
        get() = DelegatingMutableSet(
            delegate.entries,
            { Entry(key.content, value) },
            { Entry(key.caseInsensitive(), value) }
        )

    override val values: MutableCollection<Value> get() = delegate.values

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is CaseInsensitiveMap<*>) return false
        return other.delegate == delegate
    }

    override fun hashCode(): Int = delegate.hashCode()
}

private class Entry<Key, Value>(
    override val key: Key,
    override var value: Value
) : MutableMap.MutableEntry<Key, Value> {

    override fun setValue(newValue: Value): Value {
        value = newValue
        return value
    }

    override fun hashCode(): Int = 17 * 31 + key!!.hashCode() + value!!.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Map.Entry<*, *>) return false
        return other.key == key && other.value == value
    }

    override fun toString(): String = "$key=$value"
}
