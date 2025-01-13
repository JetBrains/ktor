/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.*

internal class UrlEncodedParametersBuilder(
    private val rawEncodedParametersBuilder: ParametersBuilder,
    private val decodedParametersBuilder: ParametersBuilder
) : ParametersBuilder {

    override fun build(): Parameters {
        return ParametersBuilder()
            .apply {
                appendAllEncoded(decodedParametersBuilder.build())
                appendAll(rawEncodedParametersBuilder)
            }.build()
    }

    override val caseInsensitiveName: Boolean = decodedParametersBuilder.caseInsensitiveName

    override fun getAll(name: String): List<String>? {
        val result = mutableListOf<String>()
        if (name.isDecodableToUTF8String()) {
            decodedParametersBuilder.getAll(name.decodeURLParameter())?.let { values ->
                result.addAll(values.map { value -> value.encodeURLParameterValue() })
            }
        }
        rawEncodedParametersBuilder.getAll(name)?.let {
                values ->
            result.addAll(values)
        }

        return if (result.isEmpty()) null else result
    }

    override fun contains(name: String): Boolean {
        val result = if (name.isDecodableToUTF8String()) {
            decodedParametersBuilder.contains(name.decodeURLParameter())
        } else {
            false
        }
        return result || rawEncodedParametersBuilder.contains(name)
    }

    override fun contains(name: String, value: String): Boolean {
        val result = if (name.isDecodableToUTF8String() && value.isDecodableToUTF8String(plusIsSpace = true)) {
            decodedParametersBuilder.contains(name.decodeURLParameter(), value.decodeURLParameterValue())
        } else {
            false
        }
        return result || rawEncodedParametersBuilder.contains(name, value)
    }

    override fun names(): Set<String> = mutableSetOf<String>().apply {
        addAll(decodedParametersBuilder.names().encode())
        addAll(rawEncodedParametersBuilder.names())
    }

    override fun isEmpty(): Boolean = rawEncodedParametersBuilder.isEmpty() && decodedParametersBuilder.isEmpty()

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        val entryTable = hashMapOf<String, List<String>>()
        rawEncodedParametersBuilder.entries().forEach {
            entryTable[it.key] = it.value
        }
        decodedParametersBuilder.entries().forEach {
            val key = it.key.encodeURLParameter()
            val values = it.value.map { v -> v.encodeURLParameterValue() }

            entryTable[key]?.let { v ->
                val newValues = mutableListOf<String>()
                newValues.addAll(v)
                newValues.addAll(values)
                entryTable[key] = newValues
            } ?: let { entryTable[key] = values }
        }
        return entryTable.entries
    }

    override fun set(name: String, value: String) {
        val nameIsDecodable = name.checkDecodableToUTF8String()
        val valueIsDecodable = value.checkDecodableToUTF8String(plusIsSpace = true)

        if (nameIsDecodable && valueIsDecodable) {
            decodedParametersBuilder[name.decodeURLParameter()] = value.decodeURLParameterValue()
            rawEncodedParametersBuilder.remove(name)
        } else {
            rawEncodedParametersBuilder[name] = value
            if (nameIsDecodable) {
                decodedParametersBuilder.remove(name.decodeURLParameter())
            }
        }
    }

    override fun get(name: String): String? {
        val result = if (name.isDecodableToUTF8String()) {
            decodedParametersBuilder[name.decodeURLParameter()]?.encodeURLParameterValue()
        } else {
            null
        }
        return result ?: rawEncodedParametersBuilder[name]
    }

    override fun append(name: String, value: String) {
        if (name.checkDecodableToUTF8String() && value.checkDecodableToUTF8String(plusIsSpace = true)) {
            decodedParametersBuilder.append(name.decodeURLParameter(), value.decodeURLParameterValue())
        } else {
            rawEncodedParametersBuilder.append(name, value)
        }
    }

    override fun appendAll(stringValues: StringValues) {
        stringValues.forEach { name, values -> appendAll(name, values) }
    }

    override fun appendAll(name: String, values: Iterable<String>) {
        if (!name.checkDecodableToUTF8String()) {
            rawEncodedParametersBuilder.appendAll(name, values)
        } else {
            val (decodedValues, encodedValues) = values.divide { it.checkDecodableToUTF8String(plusIsSpace = true) }
            if (decodedValues.isNotEmpty() || encodedValues.isEmpty()) {
                decodedParametersBuilder.appendAll(
                    name.decodeURLParameter(),
                    decodedValues.map { it.decodeURLParameterValue() }
                )
            }
            if (encodedValues.isNotEmpty()) {
                rawEncodedParametersBuilder.appendAll(name, encodedValues)
            }
        }
    }

    override fun appendMissing(stringValues: StringValues) {
        stringValues.forEach { name, values -> appendMissing(name, values) }
    }

    override fun appendMissing(name: String, values: Iterable<String>) {
        if (!name.checkDecodableToUTF8String()) {
            rawEncodedParametersBuilder.appendMissing(name, values)
        } else {
            val (decodedValues, encodedValues) = values.divide { it.checkDecodableToUTF8String(plusIsSpace = true) }
            decodedParametersBuilder.appendMissing(
                name.decodeURLParameter(),
                decodedValues.map { it.decodeURLParameterValue() }
            )
            rawEncodedParametersBuilder.appendMissing(name, encodedValues)
        }
    }

    override fun remove(name: String) {
        if (name.isDecodableToUTF8String()) {
            decodedParametersBuilder.remove(name.decodeURLParameter())
        }
        rawEncodedParametersBuilder.remove(name)
    }

    override fun remove(name: String, value: String): Boolean {
        return if (name.isDecodableToUTF8String() && value.isDecodableToUTF8String(plusIsSpace = true)) {
            decodedParametersBuilder.remove(name.decodeURLParameter(), value.decodeURLParameterValue())
        } else {
            rawEncodedParametersBuilder.remove(name, value)
        }
    }

    override fun removeKeysWithNoEntries() {
        rawEncodedParametersBuilder.removeKeysWithNoEntries()
        decodedParametersBuilder.removeKeysWithNoEntries()
    }

    override fun clear() {
        rawEncodedParametersBuilder.clear()
        decodedParametersBuilder.clear()
    }
}

internal fun encodeParameters(parameters: StringValues): ParametersBuilder = ParametersBuilder()
    .apply { appendAllEncoded(parameters) }

private fun StringValuesBuilder.appendAllEncoded(parameters: StringValues) {
    parameters.names()
        .forEach { key ->
            val values = parameters.getAll(key) ?: emptyList()
            appendAll(key.encodeURLParameter(), values.map { it.encodeURLParameterValue() })
        }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Collection<String>> T.encode(plusToSpace: Boolean = false): T {
    val collection = when (this) {
        is List<*> -> mutableListOf<String>()
        is Set<*> -> mutableSetOf<String>()
        else -> throw RuntimeException("Unsupported collection type ${this::class.qualifiedName}")
    }
    for (value in this) {
        collection.add(value.encodeURLParameter(plusToSpace))
    }
    return collection as T
}

private fun Iterable<String>.divide(predicate: (String) -> Boolean): Pair<List<String>, List<String>> {
    val positive = mutableListOf<String>()
    val negative = mutableListOf<String>()

    forEach {
        if (predicate(it)) {
            positive.add(it)
        } else {
            negative.add(it)
        }
    }

    return Pair(positive, negative)
}
