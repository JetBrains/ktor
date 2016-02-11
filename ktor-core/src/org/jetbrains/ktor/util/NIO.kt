package org.jetbrains.ktor.util

import java.nio.*
import java.nio.file.*

internal fun get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH(): WatchEvent.Modifier? {
    try {
        val c = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
        val f = c.getField("HIGH");
        return f.get(c) as? WatchEvent.Modifier
    } catch (e: Exception) {
        return null;
    }
}

internal fun ByteBuffer.putTo(other: ByteBuffer): Int {
    val size = Math.min(remaining(), other.remaining())
    for (i in 1..size) {
        other.put(get())
    }
    return size
}
