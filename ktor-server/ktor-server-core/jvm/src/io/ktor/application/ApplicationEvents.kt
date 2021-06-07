/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.application

/**
 * Provides events for [Application] lifecycle
 */
@Deprecated(
    "ApplicationEvents is renamed to Events",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("Events", "io.ktor.events.Events")
)
public typealias ApplicationEvents = io.ktor.events.Events

/**
 * Specifies signature for the event handler
 */
@Deprecated(
    "EventHandler<T> is moved to different package",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("EventHandler<T>", "io.ktor.events.EventHandler")
)
public typealias EventHandler<T> = io.ktor.events.EventHandler<T>

/**
 * Definition of an event.
 * Event is used as a key so both [hashCode] and [equals] need to be implemented properly.
 * Inheriting of this class is an experimental feature.
 * Instantiate directly if inheritance not necessary.
 *
 * @param T specifies what is a type of a value passed to the event
 */
@Deprecated(
    "EventDefinition<T> is moved to different package",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("Events", "io.ktor.events.EventDefinition")
)
public typealias EventDefinition<T> = io.ktor.events.EventDefinition<T>
