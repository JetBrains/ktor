/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.*
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Helper interface to test client.
 */
actual abstract class ClientLoader actual constructor(val timeoutSeconds: Int) {

    private val engines: List<HttpClientEngineContainer> by lazy {
        HttpClientEngineContainer::class.java.let { engineContainerClass ->
            ServiceLoader.load(engineContainerClass, engineContainerClass.classLoader).toList()
        }
    }

    /**
     * Perform test against all clients from dependencies.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    actual fun clientTests(
        skipEngines: List<String>,
        onlyWithEngine: String?,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ) {
        DebugProbes.install()
        for (engine in engines) {
            if (shouldSkip(engine, skipEngines, onlyWithEngine)) {
                continue
            }
            runBlocking {
                withTimeout(timeoutSeconds.seconds.inWholeMilliseconds) {
                    testWithEngine(engine.factory, this@ClientLoader, timeoutSeconds * 1000L, block)
                }
            }
        }
    }

    fun shouldSkip(engine: HttpClientEngineContainer, skipEngines: List<String>, onlyWithEngine: String?): Boolean =
        skipEngines.any { shouldSkip(engine.toString(), it, onlyWithEngine) }

    fun shouldSkip(engineName: String, skipEngine: String, onlyWithEngine: String?): Boolean {
        val locale = Locale.getDefault()
        val skipEngineArray = skipEngine.lowercase(locale).split(":")

        val (platform, skipEngineName) = when (skipEngineArray.size) {
            2 -> skipEngineArray[0] to skipEngineArray[1]
            1 -> "*" to skipEngineArray[0]
            else -> throw IllegalStateException("Wrong skip engine format, expected 'engine' or 'platform:engine'")
        }

        val platformShouldBeSkipped = "*" == platform || OS_NAME == platform
        val engineShouldBeSkipped = "*" == skipEngineName || engineName.lowercase(locale) == skipEngineName.lowercase(
            locale
        )
        val notOnlyEngine = onlyWithEngine != null && engineName.lowercase(locale) != onlyWithEngine.lowercase(locale)

        return (engineShouldBeSkipped && platformShouldBeSkipped) || notOnlyEngine
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    actual fun dumpCoroutines() {
        DebugProbes.dumpCoroutines()

        println("Thread Dump")
        Thread.getAllStackTraces().forEach { (thread, stackTrace) ->
            println("Thread: $thread")
            stackTrace.forEach {
                println("\t$it")
            }
        }
    }

    /**
     * Issues to fix before unlock:
     * 1. Pinger & Ponger in ws
     * 2. Nonce generator
     */
    // @After
    @OptIn(ExperimentalCoroutinesApi::class)
    fun waitForAllCoroutines() {
        check(DebugProbes.isInstalled) {
            "Debug probes isn't installed."
        }

        val info = DebugProbes.dumpCoroutinesInfo()

        if (info.isEmpty()) {
            return
        }

        val message = buildString {
            appendLine("Test failed. There are running coroutines")
            appendLine(info.dump())
        }

        error(message)
    }
}

private val OS_NAME: String
    get() {
        val os = System.getProperty("os.name", "unknown").lowercase(Locale.getDefault())
        return when {
            os.contains("win") -> "win"
            os.contains("mac") -> "mac"
            os.contains("nux") -> "unix"
            else -> "unknown"
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
private fun List<CoroutineInfo>.dump(): String = buildString {
    this@dump.forEach { info ->
        appendLine("Coroutine: $info")
        info.lastObservedStackTrace().forEach {
            appendLine("\t$it")
        }
    }
}
