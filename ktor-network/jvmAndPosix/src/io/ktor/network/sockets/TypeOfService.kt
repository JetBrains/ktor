/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import kotlin.jvm.*

/**
 * An inline class to hold a IP ToS value
 * @property value an unsigned byte IP_TOS value
 */
@JvmInline
public value class TypeOfService(public val value: UByte) {
    /**
     * Creates ToS by integer value discarding extra high bits
     */
    public constructor(value: Int) : this(value.toUByte())

    /**
     * Integer representation of this ToS
     */
    public inline val intValue: Int get() = value.toInt()

    public companion object {
        public val UNDEFINED: TypeOfService = TypeOfService(0u)
        public val IPTOS_LOWCOST: TypeOfService = TypeOfService(0x02u)
        public val IPTOS_RELIABILITY: TypeOfService = TypeOfService(0x04u)
        public val IPTOS_THROUGHPUT: TypeOfService = TypeOfService(0x08u)
        public val IPTOS_LOWDELAY: TypeOfService = TypeOfService(0x10u)
    }
}
