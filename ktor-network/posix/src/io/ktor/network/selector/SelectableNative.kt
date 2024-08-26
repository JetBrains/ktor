/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.selector

public actual interface Selectable {
    public val descriptor: Int
}

internal class SelectableNative(override val descriptor: Int) : Selectable {
    override fun toString(): String = "Selectable[$descriptor]"
}
