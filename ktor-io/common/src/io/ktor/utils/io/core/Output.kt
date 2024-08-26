package io.ktor.utils.io.core

import io.ktor.utils.io.IO_DEPRECATION_MESSAGE

/**
 * This shouldn't be implemented directly. Inherit [Output] instead.
 */
@Deprecated(IO_DEPRECATION_MESSAGE, replaceWith = ReplaceWith("Sink", "kotlinx.io"))
public typealias Output = kotlinx.io.Sink
