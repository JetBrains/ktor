package io.ktor.jetty

import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.server.*
import io.ktor.host.*
import io.ktor.util.*
import java.util.concurrent.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
class JettyApplicationHost(environment: ApplicationHostEnvironment, configure: Configuration.() -> Unit) : JettyApplicationHostBase(environment, configure) {

    private val dispatcher = DispatcherWithShutdown(server.threadPool.asCoroutineDispatcher())

    override fun start(wait: Boolean) : JettyApplicationHost {
        server.handler = JettyKtorHandler(environment, this::pipeline, dispatcher)
        super.start(wait)
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        dispatcher.prepareShutdown()
        try {
            super.stop(gracePeriod, timeout, timeUnit)
        } finally {
            dispatcher.completeShutdown()
        }
    }
}
