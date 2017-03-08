package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import io.netty.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.concurrent.atomic.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    val httpRequest: HttpRequest,
                                    contentQueue: NettyContentQueue) : BaseApplicationCall(application) {

    var completed: Boolean = false

    private val closed = AtomicBoolean(false)

    override val request = NettyApplicationRequest(httpRequest, NettyConnectionPoint(httpRequest, context), contentQueue)
    override val response = NettyApplicationResponse(this, respondPipeline, context)
    override val bufferPool = NettyByteBufferPool(context)

    suspend override fun respond(message: Any) {
        super.respond(message)

        completed = true
        response.close()
        request.close()

        if (closed.compareAndSet(false, true)) {
            val finishContent = context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
            if (!HttpUtil.isKeepAlive(httpRequest)) {
                // close channel if keep-alive was not requested
                finishContent.addListener(ChannelFutureListener.CLOSE)
            } else {
                // reenable read operations on a channel if keep-alive was requested
                finishContent.addListener {
                    // remove finished content queue, handler will install new
                    // TODO: change it to shareable context-agnostic concurrent map
                    try {
                        with (context.pipeline()) {
                            get("chunked")?.let { remove(it) }
                            remove(HttpContentQueue::class.java)
                            remove(NettyApplicationCallHandler::class.java)
                        }
                    } catch (ignore: NoSuchElementException) {
                    }

                    context.channel().config().isAutoRead = true
                    context.read()
                }
            }
            ReferenceCountUtil.release(httpRequest)
        }
    }

    suspend override fun respondFromBytes(bytes: ByteArray) {
        response.header(HttpHeaders.ContentLength, bytes.size)
        response.sendResponseMessage(flush = false, chunked = false)
        val buf = context.alloc().ioBuffer(bytes.size).writeBytes(bytes)
        context.writeAndFlush(buf).suspendAwait()
    }

    override suspend fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade) {
        future(context.channel().eventLoop().toCoroutineDispatcher()) {
            val upgradeContentQueue = RawContentQueue(context)

            context.channel().pipeline().replace(HttpContentQueue::class.java, "WebSocketReadQueue", upgradeContentQueue).queue.clear {
                if (it is LastHttpContent)
                    it.release()
                else
                    upgradeContentQueue.queue.push(it, false)
            }

            with(context.channel().pipeline()) {
                get("chunked")?.let { remove(it) }
                remove(NettyHostHttp1Handler::class.java)
                addFirst(NettyDirectDecoder())
            }

            commitHeaders(upgrade)

            response.sendResponseMessage(chunked = false)?.addListener {
                future(context.channel().eventLoop().toCoroutineDispatcher()) {
                    context.channel().pipeline().remove(HttpServerCodec::class.java)
                    context.channel().pipeline().addFirst(NettyDirectEncoder())

                    upgrade.upgrade(this@NettyApplicationCall, this, HttpContentReadChannel(upgradeContentQueue.queue, buffered = false), responseChannel(), Closeable {
                        context.channel().close().get()
                        upgradeContentQueue.close()
                    })
                    context.read()
                }
            } ?: throw IllegalStateException("Response has been already sent")
        }.await()
    }

    override fun responseChannel(): WriteChannel = response.responseChannel.value
}