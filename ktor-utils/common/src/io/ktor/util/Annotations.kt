/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

/**
 * API marked with this annotation is ktor internal and it is not intended to be used outside.
 * It could be modified or removed without any notice. Using it outside of ktor could cause undefined behaviour and/or
 * any strange effects.
 *
 * We are strongly recommend to not use such API.
 */
@Experimental(level = Experimental.Level.ERROR)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR
)
annotation class InternalAPI

/**
 * API marked with this annotation is experimental and is not guaranteed to be stable.
 */
@Experimental(level = Experimental.Level.WARNING)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR
)
annotation class KtorExperimentalAPI
