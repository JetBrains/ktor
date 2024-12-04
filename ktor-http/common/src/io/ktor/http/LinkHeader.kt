/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Represents a `Link` header value as per RFC 5988
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.http.LinkHeader)
 */
public class LinkHeader(
    uri: String,
    params: List<HeaderValueParam>
) : HeaderValueWithParameters("<$uri>", params) {

    @Suppress("unused")
    public constructor(uri: String, rel: String) : this(uri, listOf(HeaderValueParam(Parameters.Rel, rel)))

    public constructor(uri: String, vararg rel: String) : this(
        uri,
        listOf(HeaderValueParam(Parameters.Rel, rel.joinToString(" ")))
    )

    @Suppress("unused")
    public constructor(
        uri: String,
        rel: List<String>,
        type: ContentType
    ) : this(
        uri,
        listOf(
            HeaderValueParam(Parameters.Rel, rel.joinToString(" ")),
            HeaderValueParam(Parameters.Type, type.toString())
        )
    )

    /**
     * Link URI part
     */
    public val uri: String
        get() = content.removePrefix("<").removeSuffix(">")

    /**
     * Known Link header parameters
     */
    @Suppress("unused", "KDocMissingDocumentation", "PublicApiImplicitType")
    public object Parameters {
        public const val Rel: String = "rel"
        public const val Anchor: String = "anchor"
        public const val Rev: String = "Rev"
        public const val HrefLang: String = "hreflang"
        public const val Media: String = "media"
        public const val Title: String = "title"
        public const val Type: String = "type"
    }

    /**
     * Known rel parameter values
     */
    @Suppress("unused", "KDocMissingDocumentation", "PublicApiImplicitType")
    public object Rel {
        public const val Stylesheet: String = "stylesheet"

        public const val Prefetch: String = "prefetch"
        public const val DnsPrefetch: String = "dns-prefetch"
        public const val PreConnect: String = "preconnect"
        public const val PreLoad: String = "preload"
        public const val PreRender: String = "prerender"
        public const val Next: String = "next"
    }
}
