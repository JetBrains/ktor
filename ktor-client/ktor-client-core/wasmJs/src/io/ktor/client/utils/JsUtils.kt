/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import org.khronos.webgl.*

internal fun <T : JsAny> makeJsObject(): T = js("{ return {}; }")

@Suppress("UNUSED_PARAMETER")
internal fun <T : JsAny> makeJsNew(ctor: JsAny): T = js("new ctor()")

@PublishedApi
@Suppress("UNUSED_PARAMETER")
internal fun <T : JsAny> makeJsCall(func: JsAny, vararg arg: JsAny): T = js("func.apply(null, arg)")

@PublishedApi
@Suppress("UNUSED_PARAMETER")
internal fun makeJsCall(func: JsAny, vararg arg: JsAny): Unit = js("func.apply(null, arg)")

@Suppress("UNUSED_PARAMETER")
internal fun <T : JsAny> makeRequire(name: String): T = js("require(name)")

@Suppress("UNUSED_PARAMETER")
private fun setObjectField(obj: JsAny, name: String, value: JsAny): Unit = js("obj[name]=value")

internal operator fun JsAny.set(name: String, value: JsAny) =
    setObjectField(this, name, value)

internal operator fun JsAny.set(name: String, value: String) =
    setObjectField(this, name, value.toJsString())

internal fun Uint8Array.asByteArray(): ByteArray =
    ByteArray(length) { this[it] }

@Suppress("UNUSED_PARAMETER")
private fun toJsArrayImpl(vararg x: Byte): Uint8Array = js("new Uint8Array(x)")

internal fun ByteArray.asJsArray(): Uint8Array = toJsArrayImpl(*this)
