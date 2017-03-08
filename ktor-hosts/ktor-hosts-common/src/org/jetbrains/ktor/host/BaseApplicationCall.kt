package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.nio.*

abstract class BaseApplicationCall(override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()

    protected val respondPipeline = ApplicationResponsePipeline()

    var responded = false
        private set

    suspend override fun respond(message: Any) {
        val phases = respondPipeline.phases
        val pipelineContext = PipelineContext(phases.interceptors(), message)
        pipelineContext.proceed()
        if (responded)
            return
        responded = true
        val value = pipelineContext.subject
        when (value) {
            is FinalContent -> respondFinalContent(value)
            is ProtocolUpgrade -> pipelineContext.handleUpgrade(value)
        }
        pipelineContext.finish()
    }

    protected fun commitHeaders(o: HostResponse) {
        o.status?.let { response.status(it) } ?: response.status() ?: response.status(HttpStatusCode.OK)
        for ((name, values) in o.headers.entries()) {
            for (value in values) {
                response.header(name, value)
            }
        }

        val connection = request.headers["Connection"]
        if (connection != null) {
            when {
                connection.equals("close", true) -> response.header("Connection", "close")
                connection.equals("keep-alive", true) -> response.header("Connection", "keep-alive")
            }
        }
    }

    protected open suspend fun respondFinalContent(content: FinalContent) {
        commitHeaders(content)
        return when (content) {
        // ByteArrayContent is most efficient
            is FinalContent.ByteArrayContent -> respondFromBytes(content.bytes())

        // WriteChannelContent is more efficient than ReadChannelContent
            is FinalContent.WriteChannelContent -> content.writeTo(responseChannel())

        // Pipe is least efficient
            is FinalContent.ReadChannelContent -> respondFromChannel(content.readFrom())

        // Do nothing, but maintain `when` exhaustiveness
            is FinalContent.NoContent -> { /* no-op */
            }
        }
    }

    protected open suspend fun respondFromBytes(bytes: ByteArray) {
        val response = responseChannel()
        response.write(ByteBuffer.wrap(bytes))
    }

    protected open suspend fun respondFromChannel(channel: ReadChannel) {
        // note: it is important to open response channel before we open content channel
        // otherwise we can hit deadlock on event-based hosts

        val response = responseChannel()
        channel.copyTo(response, bufferPool, 65536)
        channel.close()
    }

    protected abstract suspend fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade)
    protected abstract fun responseChannel(): WriteChannel
    protected open val bufferPool: ByteBufferPool get() = NoPool


    override val parameters: ValuesMap by lazy { request.queryParameters + request.content.get() }
}