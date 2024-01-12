/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused", "UNUSED_PARAMETER")

package io.ktor.tests.hosts

import com.typesafe.config.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.slf4j.helpers.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*
import kotlin.test.*

class EmbeddedServerReloadingTests {

    @Test
    fun `top level extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(Application::topLevelExtensionFunction.fqName)
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("topLevelExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `top level extension function as module function reloading stress`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.deployment.watch" to listOf("ktor-server-core"),
                        "ktor.application.modules" to listOf(Application::topLevelExtensionFunction.fqName)
                    )
                )
            )
        }

        val props = applicationProperties(environment)
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        repeat(100) {
            server.reload()
        }
        server.stop()
    }

    @Test
    fun `top level non-extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf<KFunction0<Unit>>(::topLevelFunction).map { it.fqName }
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("topLevelFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `companion object extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Companion::class.jvmName + "." + "companionObjectExtensionFunction"
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("companionObjectExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `companion object non-extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Companion::class.functionFqName("companionObjectFunction")
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("companionObjectFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `companion object jvmstatic extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Companion::class.jvmName + "." + "companionObjectJvmStaticExtensionFunction"
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("companionObjectJvmStaticExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `companion object jvmstatic non-extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            Companion::class.functionFqName("companionObjectJvmStaticFunction")
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("companionObjectJvmStaticFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `object holder extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            ObjectModuleFunctionHolder::class.jvmName + "." + "objectExtensionFunction"
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("objectExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `object holder non-extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            ObjectModuleFunctionHolder::class.functionFqName("objectFunction")
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("objectFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `class holder extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            ClassModuleFunctionHolder::class.jvmName + "." + "classExtensionFunction"
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("classExtensionFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `class holder non-extension function as module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(ClassModuleFunctionHolder::classFunction.fqName)
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("classFunction", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `no-arg module function`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(NoArgModuleFunction::class.functionFqName("main"))
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals(1, NoArgModuleFunction.result)
        server.stop()
    }

    @Test
    fun `multiple module functions`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(MultipleModuleFunctions::class.jvmName + ".main")
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("best function called", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `multiple static module functions`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(MultipleStaticModuleFunctions::class.jvmName + ".main")
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("best function called", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `top level module function with default arg`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            EmbeddedServerReloadingTests::class.jvmName + "Kt.topLevelWithDefaultArg"
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("topLevelWithDefaultArg", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `static module function with default arg`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(Companion::class.jvmName + ".functionWithDefaultArg")
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("functionWithDefaultArg", application.attributes[TestKey])
        server.stop()
    }

    @Test
    fun `top level module function with jvm overloads`() {
        val environment = applicationEnvironment {
            config = HoconApplicationConfig(
                ConfigFactory.parseMap(
                    mapOf(
                        "ktor.deployment.environment" to "test",
                        "ktor.application.modules" to listOf(
                            EmbeddedServerReloadingTests::class.jvmName + "Kt.topLevelWithJvmOverloads"
                        )
                    )
                )
            )
        }
        val props = applicationProperties(environment) {
            developmentMode = false
        }
        val server = EmbeddedServer(props, DummyEngineFactory)

        server.start()
        val application = server.application
        assertNotNull(application)
        assertEquals("topLevelWithJvmOverloads", application.attributes[TestKey])
        server.stop()
    }

    object NoArgModuleFunction {
        var result = 0

        fun main() {
            result++
        }
    }

    object MultipleModuleFunctions {
        fun main() {
        }

        fun main(app: Application) {
            app.attributes.put(TestKey, "best function called")
        }
    }

    object MultipleStaticModuleFunctions {
        @JvmStatic
        fun main() {
        }

        @JvmStatic
        fun main(app: Application) {
            app.attributes.put(TestKey, "best function called")
        }
    }

    class ClassModuleFunctionHolder {
        @Suppress("UNUSED")
        fun Application.classExtensionFunction() {
            attributes.put(TestKey, "classExtensionFunction")
        }

        fun classFunction(app: Application) {
            app.attributes.put(TestKey, "classFunction")
        }
    }

    object ObjectModuleFunctionHolder {
        @Suppress("UNUSED")
        fun Application.objectExtensionFunction() {
            attributes.put(TestKey, "objectExtensionFunction")
        }

        fun objectFunction(app: Application) {
            app.attributes.put(TestKey, "objectFunction")
        }
    }

    companion object {
        val TestKey = AttributeKey<String>("test-key")

        private val KFunction<*>.fqName: String
            get() = javaMethod!!.declaringClass.name + "." + name

        private fun KClass<*>.functionFqName(name: String) = "$jvmName.$name"

        @Suppress("UNUSED")
        fun Application.companionObjectExtensionFunction() {
            attributes.put(TestKey, "companionObjectExtensionFunction")
        }

        fun companionObjectFunction(app: Application) {
            app.attributes.put(TestKey, "companionObjectFunction")
        }

        @Suppress("UNUSED")
        @JvmStatic
        fun Application.companionObjectJvmStaticExtensionFunction() {
            attributes.put(TestKey, "companionObjectJvmStaticExtensionFunction")
        }

        @JvmStatic
        fun companionObjectJvmStaticFunction(app: Application) {
            app.attributes.put(TestKey, "companionObjectJvmStaticFunction")
        }

        @JvmStatic
        fun Application.functionWithDefaultArg(test: Boolean = false) {
            attributes.put(TestKey, "functionWithDefaultArg")
        }
    }

    @Test
    fun `application is available before environment start`() {
        val env = dummyEnv()
        val props = applicationProperties(env)
        val server = EmbeddedServer(props, DummyEngineFactory)
        val app = server.application
        server.start()
        assertEquals(app, server.application)
    }

    @Test
    fun `completion handler is invoked when attached before environment start`() {
        val env = dummyEnv()
        val props = applicationProperties(env)
        val server = EmbeddedServer(props, DummyEngineFactory)
        val job = server.application.coroutineContext[Job]!!

        var invoked = false
        job.invokeOnCompletion {
            invoked = true
        }

        server.start()
        server.stop()

        runBlocking { job.join() }

        assertTrue(invoked, "On completion handler wasn't invoked")
    }

    @Test
    fun `interceptor is invoked when added before environment start`() {
        val server = EmbeddedServer(applicationProperties(), TestEngine) {}
        val engine = server.engine
        server.application.intercept(ApplicationCallPipeline.Plugins) {
            call.response.header("Custom", "Value")
        }
        server.start()

        try {
            server.application.routing {
                get("/") {
                    call.respondText { "Hello" }
                }
            }

            assertEquals("Value", engine.handleRequest(HttpMethod.Get, "/").response.headers["Custom"])
        } catch (cause: Throwable) {
            fail("Failed with an exception: ${cause.message}")
        } finally {
            server.stop(0L, 0L)
        }
    }

    private fun dummyEnv() = applicationEnvironment {
        classLoader = this::class.java.classLoader
        log = NOPLogger.NOP_LOGGER
        config = MapApplicationConfig()
    }

    private object DummyEngineFactory :
        ApplicationEngineFactory<DummyEngine, BaseApplicationEngine.Configuration> {

        override fun configuration(
            configure: BaseApplicationEngine.Configuration.() -> Unit
        ): BaseApplicationEngine.Configuration {
            return BaseApplicationEngine.Configuration().apply(configure)
        }

        override fun create(
            environment: ApplicationEnvironment,
            monitor: Events,
            developmentMode: Boolean,
            configuration: BaseApplicationEngine.Configuration,
            applicationProvider: () -> Application
        ): DummyEngine {
            return DummyEngine
        }
    }

    private object DummyEngine : BaseApplicationEngine(applicationEnvironment { }, Events(), false) {
        override fun start(wait: Boolean): ApplicationEngine = this

        override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {}
    }
}

// some weirdness with the compiler ignores default in expect
fun EmbeddedServer<*, *>.start() = start(wait = false)

fun Application.topLevelExtensionFunction() {
    attributes.put(EmbeddedServerReloadingTests.TestKey, "topLevelExtensionFunction")
}

fun topLevelFunction(app: Application) {
    app.attributes.put(EmbeddedServerReloadingTests.TestKey, "topLevelFunction")
}

@Suppress("unused")
fun topLevelFunction() {
    error("Shouldn't be invoked")
}

fun Application.topLevelWithDefaultArg(testing: Boolean = false) {
    attributes.put(EmbeddedServerReloadingTests.TestKey, "topLevelWithDefaultArg")
}

@JvmOverloads
fun Application.topLevelWithJvmOverloads(testing: Boolean = false) {
    attributes.put(EmbeddedServerReloadingTests.TestKey, "topLevelWithJvmOverloads")
}
