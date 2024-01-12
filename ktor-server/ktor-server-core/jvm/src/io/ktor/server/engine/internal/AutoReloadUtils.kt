/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine.internal

import io.ktor.server.application.*
import java.lang.reflect.*
import java.nio.file.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

internal val currentStartupModules = ThreadLocal<MutableList<String>>()
internal val ApplicationEnvironmentClassInstance = ApplicationEnvironment::class.java
internal val ApplicationClassInstance = Application::class.java

internal fun isApplicationEnvironment(parameter: KParameter): Boolean =
    isParameterOfType(parameter, ApplicationEnvironmentClassInstance)

internal fun isApplication(parameter: KParameter): Boolean =
    isParameterOfType(parameter, ApplicationClassInstance)

internal fun ClassLoader.loadClassOrNull(name: String): Class<*>? = try {
    loadClass(name)
} catch (cause: ClassNotFoundException) {
    null
}

internal fun isParameterOfType(parameter: KParameter, type: Class<*>) =
    (parameter.type.javaType as? Class<*>)?.let { type.isAssignableFrom(it) } ?: false

internal fun <R> List<KFunction<R>>.bestFunction(): KFunction<R>? = sortedWith(
    compareBy(
        { it.parameters.isNotEmpty() && isApplication(it.parameters[0]) },
        { it.parameters.count { !it.isOptional } },
        { it.parameters.size }
    )
).lastOrNull()

internal fun KFunction<*>.isApplicableFunction(): Boolean {
    if (isOperator || isInfix || isInline || isAbstract) return false
    if (isSuspend) return false // not supported yet

    extensionReceiverParameter?.let {
        if (!isApplication(it) && !isApplicationEnvironment(it)) return false
    }

    javaMethod?.let {
        if (it.isSynthetic) return false

        // static no-arg function is useless as a module function since no application instance available
        // so nothing could be configured
        if (Modifier.isStatic(it.modifiers) && parameters.isEmpty()) {
            return false
        }
    }

    return parameters.all {
        isApplication(it) || isApplicationEnvironment(it) || it.kind == KParameter.Kind.INSTANCE || it.isOptional
    }
}

internal fun Class<*>.takeIfNotFacade(): KClass<*>? =
    if (getAnnotation(Metadata::class.java)?.takeIf { it.kind == 1 } != null) kotlin else null

@Suppress("FunctionName")
internal fun get_com_sun_nio_file_SensitivityWatchEventModifier_HIGH(): WatchEvent.Modifier? {
    if (System.getenv("ANDROID_DATA") != null) return null

    return try {
        val modifierClass = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier")
        val field = modifierClass.getField("HIGH")
        field.get(modifierClass) as? WatchEvent.Modifier
    } catch (cause: Throwable) {
        null
    }
}
