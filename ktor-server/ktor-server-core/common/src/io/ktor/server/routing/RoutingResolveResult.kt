/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.routing

import io.ktor.http.*

/**
 * Represents a result of routing resolution.
 *
 * @property route specifies a routing node for successful resolution, or nearest one for failed.
 */
public sealed class RoutingResolveResult(public val route: RoutingNode) {
    /**
     * Provides all captured values for this result.
     */
    public abstract val parameters: Parameters

    /**
     * Represents a successful result
     */
    public class Success internal constructor(
        route: RoutingNode,
        override val parameters: Parameters,
        internal val quality: Double
    ) : RoutingResolveResult(route) {

        @Deprecated(
            "This will become internal in future releases.",
            level = DeprecationLevel.ERROR
        )
        public constructor(route: RoutingNode, parameters: Parameters) : this(route, parameters, 0.0)

        override fun toString(): String = "SUCCESS${if (parameters.isEmpty()) "" else "; $parameters"} @ $route"
    }

    /**
     * Represents a failed result
     * @param reason provides information on reason of a failure
     */
    public class Failure internal constructor(
        route: RoutingNode,
        public val reason: String,
        public val errorStatusCode: HttpStatusCode
    ) : RoutingResolveResult(route) {

        @Deprecated(
            "This will become internal in future releases.",
            level = DeprecationLevel.ERROR
        )
        public constructor(route: RoutingNode, reason: String) : this(route, reason, HttpStatusCode.NotFound)

        override val parameters: Nothing
            get() = throw UnsupportedOperationException("Parameters are available only when routing resolve succeeds")

        override fun toString(): String = "FAILURE \"$reason\" @ $route"
    }
}
