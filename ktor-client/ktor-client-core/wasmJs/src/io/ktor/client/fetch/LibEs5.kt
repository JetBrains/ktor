/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fetch

public typealias ArrayBufferLike = JsAny

public external interface ArrayBufferView : JsAny {
    public var buffer: ArrayBufferLike
    public var byteLength: Int
    public var byteOffset: Int
}

public external interface ArrayBuffer : JsAny {
    public var byteLength: Int
    public fun slice(begin: Int, end: Int? = definedExternally): ArrayBuffer

//    companion object : ArrayBufferConstructor by definedExternally
}

public external interface ArrayBufferConstructor : JsAny {
    public var prototype: ArrayBuffer
    public fun isView(arg: JsAny): Boolean
}

public external interface Uint8Array : JsAny {
    public var BYTES_PER_ELEMENT: JsNumber
    public var buffer: ArrayBufferLike
    public var byteLength: Int
    public var byteOffset: Int

    public fun copyWithin(
        target: JsNumber,
        start: JsNumber,
        end: JsNumber? = definedExternally
    ): Uint8Array /* this */

    public fun every(
        callbackfn: (value: JsNumber, index: JsNumber, array: Uint8Array) -> JsAny,
        thisArg: JsAny? = definedExternally
    ): Boolean

    public fun fill(
        value: JsNumber,
        start: JsNumber? = definedExternally,
        end: JsNumber? = definedExternally
    ): Uint8Array /* this */

    public fun filter(
        callbackfn: (value: JsNumber, index: JsNumber, array: Uint8Array) -> JsAny,
        thisArg: JsAny? = definedExternally
    ): Uint8Array

    public fun find(
        predicate: (value: JsNumber, index: JsNumber, obj: Uint8Array) -> Boolean,
        thisArg: JsAny? = definedExternally
    ): JsNumber?

    public fun findIndex(
        predicate: (value: JsNumber, index: JsNumber, obj: Uint8Array) -> Boolean,
        thisArg: JsAny? = definedExternally
    ): JsNumber

    public fun forEach(
        callbackfn: (value: JsNumber, index: JsNumber, array: Uint8Array) -> Unit,
        thisArg: JsAny? = definedExternally
    )

    public fun indexOf(searchElement: JsNumber, fromIndex: JsNumber? = definedExternally): JsNumber

    public fun join(separator: String? = definedExternally): String

    public fun lastIndexOf(searchElement: JsNumber, fromIndex: JsNumber? = definedExternally): JsNumber

    public var length: Int
    public fun map(
        callbackfn: (value: JsNumber, index: JsNumber, array: Uint8Array) -> JsNumber,
        thisArg: JsAny? = definedExternally
    ): Uint8Array

    public fun reduce(
        callbackfn: (
            previousValue: JsNumber,
            currentValue: JsNumber,
            currentIndex: JsNumber,
            array: Uint8Array
        ) -> JsNumber
    ): JsNumber

    public fun reduce(
        callbackfn: (
            previousValue: JsNumber,
            currentValue: JsNumber,
            currentIndex: JsNumber,
            array: Uint8Array
        ) -> JsNumber,
        initialValue: JsNumber
    ): JsNumber

    public fun <U : JsAny> reduce(
        callbackfn: (
            previousValue: U,
            currentValue: JsNumber,
            currentIndex: JsNumber,
            array: Uint8Array
        ) -> U,
        initialValue: U
    ): U

    public fun reduceRight(
        callbackfn: (
            previousValue: JsNumber,
            currentValue: JsNumber,
            currentIndex: JsNumber,
            array: Uint8Array
        ) -> JsNumber
    ): JsNumber
    public fun reduceRight(
        callbackfn: (
            previousValue: JsNumber,
            currentValue: JsNumber,
            currentIndex: JsNumber,
            array: Uint8Array
        ) -> JsNumber,
        initialValue: JsNumber
    ): JsNumber

    public fun <U : JsAny> reduceRight(
        callbackfn: (
            previousValue: U,
            currentValue: JsNumber,
            currentIndex: JsNumber,
            array: Uint8Array
        ) -> U,
        initialValue: U
    ): U

    public fun reverse(): Uint8Array

    public fun set(array: ArrayLike<JsNumber>, offset: JsNumber? = definedExternally)

    public fun slice(start: JsNumber? = definedExternally, end: JsNumber? = definedExternally): Uint8Array

    public fun some(
        callbackfn: (value: JsNumber, index: JsNumber, array: Uint8Array) -> JsAny,
        thisArg: JsAny? = definedExternally
    ): Boolean

    public fun sort(compareFn: ((a: JsNumber, b: JsNumber) -> JsNumber)? = definedExternally): Uint8Array /* this */

    public fun subarray(begin: JsNumber? = definedExternally, end: JsNumber? = definedExternally): Uint8Array

    public fun toLocaleString(): String

    override fun toString(): String
}

@Suppress("UNUSED_PARAMETER")
internal fun getMethodImplForUint8Array(obj: Uint8Array, index: Int): Byte = js("return obj[index];")

public operator fun Uint8Array.get(index: Int): Byte = getMethodImplForUint8Array(this, index)

@Suppress("UNUSED_PARAMETER")
internal fun setMethodImplForUint8Array(
    obj: Uint8Array,
    index: Int,
    value: Byte
): Unit = js("obj[index] = value;")

public operator fun Uint8Array.set(index: Int, value: Byte): Unit =
    setMethodImplForUint8Array(this, index, value)

public external interface Uint8ArrayConstructor : JsAny {
    public var prototype: Uint8Array
    public var BYTES_PER_ELEMENT: JsNumber
    public fun of(vararg items: JsNumber): Uint8Array
    public fun from(arrayLike: ArrayLike<JsNumber>): Uint8Array
    public fun <T : JsAny> from(
        arrayLike: ArrayLike<T>,
        mapfn: (v: T, k: JsNumber) -> JsNumber,
        thisArg: JsAny? = definedExternally
    ): Uint8Array
}

public external interface ArrayLike<T : JsAny?> : JsAny {
    public var length: Int
}

@Suppress("UNUSED_PARAMETER")
internal fun <T : JsAny?> getMethodImplForArrayLike(obj: ArrayLike<T>, index: Int): T =
    js("return obj[index];")

public operator fun <T : JsAny?> ArrayLike<T>.get(index: Int): T =
    getMethodImplForArrayLike(this, index)

@Suppress("UNUSED_PARAMETER")
internal fun <T : JsAny?> setMethodImplForArrayLike(obj: ArrayLike<T>, index: Int, value: T): Unit =
    js("obj[index] = value;")

public operator fun <T : JsAny?> ArrayLike<T>.set(index: Int, value: T): Unit =
    setMethodImplForArrayLike(this, index, value)
