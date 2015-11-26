package org.jetbrains.ktor.testing

import com.typesafe.config.*
import org.jetbrains.ktor.application.*

object On

object It

@Suppress("UNUSED_PARAMETER")
fun on(comment: String, body: On.() -> Unit) = On.body()

@Suppress("UNUSED_PARAMETER")
inline fun On.it(description: String, body: It.() -> Unit) = It.body()

fun withTestApplication(test: TestApplicationHost.() -> Unit) {
    withApplication<TestApplication>(test)
}

fun createTestHost(): TestApplicationHost {
    val testConfig = ConfigFactory.parseMap(
            mapOf(
                    "ktor.deployment.environment" to "test",
                    "ktor.application.class" to TestApplication::class.qualifiedName
                 ))
    val config = ApplicationConfig(testConfig, SLF4JApplicationLog("ktor.test"))
    return TestApplicationHost(config)
}
