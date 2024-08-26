/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

public object PlatformUtils {
    public val IS_BROWSER: Boolean = when (val platform = platform) {
        is Platform.Js -> platform.jsPlatform == Platform.JsPlatform.Browser
        is Platform.WasmJs -> platform.jsPlatform == Platform.JsPlatform.Browser
        else -> false
    }
    public val IS_NODE: Boolean = when (val platform = platform) {
        is Platform.Js -> platform.jsPlatform == Platform.JsPlatform.Node
        is Platform.WasmJs -> platform.jsPlatform == Platform.JsPlatform.Node
        else -> false
    }

    public val IS_JS: Boolean = platform is Platform.Js
    public val IS_WASM_JS: Boolean = platform is Platform.WasmJs
    public val IS_JVM: Boolean = platform == Platform.Jvm
    public val IS_NATIVE: Boolean = platform == Platform.Native

    public val IS_DEVELOPMENT_MODE: Boolean = isDevelopmentMode

    public val IS_NEW_MM_ENABLED: Boolean = isNewMemoryModel
}

internal expect val PlatformUtils.isDevelopmentMode: Boolean
internal expect val PlatformUtils.isNewMemoryModel: Boolean

public expect val PlatformUtils.platform: Platform

public sealed class Platform {
    public data object Jvm : Platform()
    public data object Native : Platform()
    public data class Js(val jsPlatform: JsPlatform) : Platform()
    public data class WasmJs(val jsPlatform: JsPlatform) : Platform()

    public enum class JsPlatform { Browser, Node }
}
