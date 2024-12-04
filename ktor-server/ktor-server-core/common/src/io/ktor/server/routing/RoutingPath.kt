/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.routing

import io.ktor.http.*

/**
 * A parsed routing path. Consist of number of segments [parts].
 *
 * @property parts contains parsed routing path segments
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.routing.RoutingPath)
 */
public class RoutingPath private constructor(public val parts: List<RoutingPathSegment>) {
    public companion object {
        /**
         * A constant for a root routing path.
         *
         * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.routing.RoutingPath.Companion.root)
         */
        public val root: RoutingPath = RoutingPath(listOf())

        /**
         * Parses the specified [path] and creates an instance of [RoutingPath].
         * It handles wildcards and decodes escape characters properly.
         */
        public fun parse(path: String): RoutingPath {
            if (path == "/") return root
            val segments = path.splitToSequence("/").filter { it.isNotEmpty() }.map { segment ->
                when {
                    segment.contains('{') && segment.contains('}') -> RoutingPathSegment(
                        segment,
                        RoutingPathSegmentKind.Parameter
                    )
                    else -> RoutingPathSegment(segment.decodeURLPart(), RoutingPathSegmentKind.Constant)
                }
            }

            return RoutingPath(segments.toList())
        }
    }

    override fun toString(): String = parts.joinToString("/") { it.value }
}

/**
 * A single routing path segment.
 * @property value - segment text value
 * @property kind - segment kind (constant or parameter)
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.routing.RoutingPathSegment)
 */
public data class RoutingPathSegment(val value: String, val kind: RoutingPathSegmentKind)

/**
 * Possible routing path segment kinds.
 *
 * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.routing.RoutingPathSegmentKind)
 */
public enum class RoutingPathSegmentKind {
    /**
     * A constant path segment.
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.routing.RoutingPathSegmentKind.Constant)
     */
    Constant,

    /**
     * A parameter path segment (a wildcard, a named parameter, or both).
     *
     * [Report a problem](https://ktor.io/feedback?fqname=io.ktor.server.routing.RoutingPathSegmentKind.Parameter)
     */
    Parameter
}
