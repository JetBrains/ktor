/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.server.application.*
import java.util.concurrent.atomic.*

private val SHUTDOWN_HOOK_DISABLED = System.getProperty("io.ktor.server.engine.ShutdownHook", "true") == "false"

/**
 * Configures automatic management of JVM shutdown hooks for terminating an application.
 * This function should be invoked before starting the application engine.
 * Once the JVM termination is detected, the [stop] block will be executed.
 * Please note that a shutdown hook is registered only while the application is running.
 * If the application has already stopped, the hook won't be registered, and invoking [stop] will have no effect.
 * Therefore, the [stop] block will be called either once or not at all.
 */
public actual fun ApplicationEngine.addShutdownHook(monitor: Events, stop: () -> Unit) {
    if (SHUTDOWN_HOOK_DISABLED) return

    val hook = ShutdownHook(stop)
    monitor.subscribe(ApplicationStarting) {
        monitor.subscribe(ApplicationStopping) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook)
            } catch (alreadyShuttingDown: IllegalStateException) {
                // ignore
            }
        }

        Runtime.getRuntime().addShutdownHook(hook)
    }
}

/**
 * Adds automatic JVM shutdown hooks management. Should be used **before** starting the server.
 * Once JVM termination noticed, [stop] block will be executed.
 * Please note that a shutdown hook only registered when the application is running. If the application
 * is already stopped then there will be no hook and no [stop] function invocation possible.
 * So [stop] block will be called once or never.
 */
public actual fun EmbeddedServer<*, *>.addShutdownHook(stop: () -> Unit) {
    engine.addShutdownHook(monitor, stop)
}

private class ShutdownHook(private val stopFunction: () -> Unit) : Thread("KtorShutdownHook") {
    private val shouldStop = AtomicBoolean(true)

    override fun run() {
        if (shouldStop.compareAndSet(true, false)) {
            stopFunction()
        }
    }
}
