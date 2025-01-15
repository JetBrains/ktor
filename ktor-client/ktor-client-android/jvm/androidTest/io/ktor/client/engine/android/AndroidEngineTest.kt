/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.android

import android.net.http.ConnectionMigrationOptions
import android.net.http.HttpEngine
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains

class AndroidEngineTest {
    @Test
    fun testHttpEngine() = runTest {
        val client = HttpClient(Android.create {
            httpEngineDisabled = false
            context = InstrumentationRegistry.getInstrumentation().targetContext
        })

        val response = client.get("https://github.com/robots.txt")

        assertContains(response.bodyAsText(), "Disallow")
    }
    @Test
    fun testHttpEngineConfig() = runTest {
        val client = HttpClient(Android.create {
            httpEngineDisabled = false
            context = InstrumentationRegistry.getInstrumentation().targetContext

            httpEngineConfig = {
                setUserAgent("Xyz")

                setEnableBrotli(true)
                setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_IN_MEMORY, 10_000_000)
                setConnectionMigrationOptions(
                    ConnectionMigrationOptions.Builder()
                        .setDefaultNetworkMigration(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                        .setPathDegradationMigration(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                        .setAllowNonDefaultNetworkUsage(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                        .build(),
                )
                addQuicHint("github.com", 443, 443)
            }
        })

        val response = client.get("https://github.com/robots.txt")

        assertContains(response.bodyAsText(), "Disallow")
    }

    @Test
    fun testUrlConnection() = runTest {
        val client = HttpClient(Android.create {
            httpEngineDisabled = true
        })

        val response = client.get("https://github.com/robots.txt")

        println(response.bodyAsText())
        assertContains(response.bodyAsText(), "Disallow")
    }
}
