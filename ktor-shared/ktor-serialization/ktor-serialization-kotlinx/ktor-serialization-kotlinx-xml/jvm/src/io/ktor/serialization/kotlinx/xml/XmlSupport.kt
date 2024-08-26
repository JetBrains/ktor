/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.xml

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import kotlinx.serialization.*
import nl.adaptivity.xmlutil.*
import nl.adaptivity.xmlutil.serialization.*

/**
 * The default XML configuration. The settings are:
 * - Every declaration without a namespace is automatically wrapped in the namespace.
 *       See also [XMLOutputFactory.IS_REPAIRING_NAMESPACES].
 * - The XML declaration is not generated.
 * - The indent is empty.
 * - Polymorphic serialization is disabled.
 *
 * See [XML] for more details.
 */
@OptIn(XmlUtilInternal::class)
public val DefaultXml: XML = XML {
    repairNamespaces = true
    xmlDeclMode = XmlDeclMode.None
    indentString = ""
    autoPolymorphic = false
    this.xmlDeclMode
}

/**
 * Registers the `application/xml` (or another specified [contentType]) content type
 * to the [ContentNegotiation] plugin using kotlinx.serialization.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 *
 * @param format instance. [DefaultXml] is used by default
 * @param contentType to register with, `application/xml` by default
 */
public fun Configuration.xml(
    format: XML = DefaultXml,
    contentType: ContentType = ContentType.Application.Xml
) {
    serialization(contentType, format)
}
