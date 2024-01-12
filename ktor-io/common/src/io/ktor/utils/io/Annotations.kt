/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

/**
 * API marked with this annotation is internal, and it is not intended to be used outside Ktor.
 * It could be modified or removed without any notice. Using it outside Ktor could cause undefined behaviour and/or
 * any unexpected effects.
 */
@Suppress("DEPRECATION")
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal in Ktor and should not be used. It could be removed or changed without notice."
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY_SETTER
)
public annotation class InternalAPI

/**
 * API marked with this annotation is experimental and is not guaranteed to be stable.
 */
@Suppress("DEPRECATION")
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API is experimental. " +
        "It could be removed or changed in future releases, or its behaviour may be different."
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR
)
@Deprecated(
    "This annotation is no longer used and there is no need to opt-in into it.",
    level = DeprecationLevel.ERROR
)
public annotation class KtorExperimentalAPI

/**
 * API marked with this annotation is intended to become public in the future [version].
 * Usually it means that the API can't be public at the moment of development due to
 * compatibility guarantees restrictions.
 *
 * Marking a public declaration with this annotation makes no sense
 * except for the case when it is also marked with [InternalAPI].
 *
 * Please note that the specified [version] and the fact of making something a candidate is not a guarantee,
 * so the target version could be changed without any notice or even the promotion could be cancelled at all.
 *
 * @property version in which the API is planned to be promoted
 */
@InternalAPI
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS
)
public annotation class PublicAPICandidate(val version: String)

/**
 * A marker annotations for DSLs.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
public annotation class KtorDsl
