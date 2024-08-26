package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.IOException
import java.io.*
import java.nio.*
import kotlin.coroutines.*

/**
 * Open a channel and launch a coroutine to copy bytes from the input stream to the channel.
 * Please note that it may block your async code when started on [Dispatchers.Unconfined]
 * since [InputStream] is blocking on it's nature
 */
@Suppress("UNUSED_PARAMETER")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteBuffer>
): ByteReadChannel = RawSourceChannel(asSource(), context)

/**
 * Open a channel and launch a coroutine to copy bytes from the input stream to the channel.
 * Please note that it may block your async code when started on [Dispatchers.Unconfined]
 * since [InputStream] is blocking on it's nature
 */
@Suppress("UNUSED_PARAMETER")
@JvmName("toByteReadChannelWithArrayPool")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteArray> = ByteArrayPool
): ByteReadChannel = RawSourceChannel(asSource(), context)

internal class RawSourceChannel(
    private val source: RawSource,
    private val parent: CoroutineContext
) : ByteReadChannel {
    private var closedToken: CloseToken? = null
    private val buffer = Buffer()

    override val closedCause: Throwable?
        get() = closedToken?.cause

    override val isClosedForRead: Boolean
        get() = closedToken != null && buffer.exhausted()

    val job = Job(parent[Job])
    val coroutineContext = parent + job + CoroutineName("RawSourceChannel")

    @InternalAPI
    override val readBuffer: Source
        get() = buffer

    override suspend fun awaitContent(min: Int): Boolean {
        if (closedToken != null) return true

        withContext(coroutineContext) {
            var result = 0L
            while (buffer.size < min && result >= 0) {
                result = try {
                    source.readAtMostTo(buffer, Long.MAX_VALUE)
                } catch (cause: EOFException) {
                    -1L
                }
            }

            if (result == -1L) {
                source.close()
                job.complete()
                closedToken = CloseToken(null)
            }
        }

        return closedToken != null
    }

    override fun cancel(cause: Throwable?) {
        if (closedToken != null) return
        job.cancel(cause?.message ?: "Channel was cancelled", cause)
        source.close()
        closedToken = CloseToken(IOException(cause?.message ?: "Channel was cancelled", cause))
    }
}
