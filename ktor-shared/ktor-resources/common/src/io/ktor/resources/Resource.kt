/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources

import kotlinx.serialization.*

/**
 * An annotation for classes that act as [typed routes](https://ktor.io/docs/type-safe-routing.html).
 * All annotated types should be [Serializable].
 *
 * Every property that has a corresponding placeholder inside [path] is used as a value for this placeholder.
 * Other properties are put into the URL query.
 * Example:
 * ```
 * @Resource("/users/{id}")
 * data class UserById(val id: Long, val properties: List<String>)
 *
 * val userById = UserById(id = 123, properties = listOf("name", "avatar"))
 * val url = href(userById)
 * assertEquals("/users/123?properties=name&properties=avatar")
 * ```
 * Properties can be primitives or types annotated with the [Serializable] annotation.
 *
 * You can nest class for better organization, but all nested classes should have a property with an outer class type.
 * Example:
 * ```kotlin
 * @Resource("/users")
 * data class Users {
 *   @Resource("/{id}")
 *   data class ById(val parent: Users = Users(), val id: Long)
 *
 *   @Resource("/add")
 *   data class Add(val parent: Users = Users(), val name: String)
 * }
 * val userById = Users.ById(123)
 * val addUser = Users.add("new_name")
 * ```
 *
 * @property path the route path, including the class property names wrapped with curly braces.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.resources.Resource)
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
@MetaSerializable
public annotation class Resource(val path: String)
