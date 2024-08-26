package io.ktor.utils.io.core.internal

import io.ktor.utils.io.*
import kotlinx.io.*

@Deprecated(IO_DEPRECATION_MESSAGE, replaceWith = ReplaceWith("Buffer", "kotlinx.io"))
public typealias ChunkBuffer = kotlinx.io.Buffer

public val Buffer.writeRemaining: Int get() = Int.MAX_VALUE
