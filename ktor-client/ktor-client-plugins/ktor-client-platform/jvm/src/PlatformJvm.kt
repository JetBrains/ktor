/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.platform

import java.lang.reflect.InvocationTargetException

internal actual object Platform {
    /**
     * This explicit check avoids activating in Android Studio with Android specific classes available when running plugins inside the IDE.
     */
    private val isAndroid: Boolean = System.getProperty("java.vm.name") == "Dalvik"

    /**
     * Used by Android to determine whether cleartext network traffic is permitted for all network communication from this process.
     *
     * See [NetworkSecurityPolicy#isCleartextTrafficPermitted()](https://developer.android.com/reference/android/security/NetworkSecurityPolicy#isCleartextTrafficPermitted()).
     */
    actual fun isCleartextTrafficPermitted(hostname: String): Boolean {
        if (!isAndroid) return true
        return try {
            val networkPolicyClass = Class.forName("android.security.NetworkSecurityPolicy")
            val getInstanceMethod = networkPolicyClass.getMethod("getInstance").apply { isAccessible = true }
            val networkSecurityPolicy = getInstanceMethod.invoke(null)
            api24IsCleartextTrafficPermitted(hostname, networkPolicyClass, networkSecurityPolicy)
        } catch (_: ClassNotFoundException) {
            true
        } catch (_: NoSuchMethodException) {
            true
        } catch (e: IllegalAccessException) {
            throw AssertionError("unable to determine cleartext support", e)
        } catch (e: IllegalArgumentException) {
            throw AssertionError("unable to determine cleartext support", e)
        } catch (e: InvocationTargetException) {
            throw AssertionError("unable to determine cleartext support", e)
        }
    }

    @Throws(InvocationTargetException::class, IllegalAccessException::class)
    private fun api24IsCleartextTrafficPermitted(
        hostname: String,
        networkPolicyClass: Class<*>,
        networkSecurityPolicy: Any
    ): Boolean = try {
        val isCleartextTrafficPermittedMethod = networkPolicyClass
            .getMethod("isCleartextTrafficPermitted", String::class.java)
            .apply { isAccessible = true }
        isCleartextTrafficPermittedMethod.invoke(networkSecurityPolicy, hostname) as Boolean
    } catch (_: NoSuchMethodException) {
        api23IsCleartextTrafficPermitted(networkPolicyClass, networkSecurityPolicy)
    }

    @Throws(InvocationTargetException::class, IllegalAccessException::class)
    private fun api23IsCleartextTrafficPermitted(
        networkPolicyClass: Class<*>,
        networkSecurityPolicy: Any
    ): Boolean = try {
        val isCleartextTrafficPermittedMethod = networkPolicyClass
            .getMethod("isCleartextTrafficPermitted")
            .apply { isAccessible = true }
        isCleartextTrafficPermittedMethod.invoke(networkSecurityPolicy) as Boolean
    } catch (_: NoSuchMethodException) {
        true
    }
}
