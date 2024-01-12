/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.routing

import io.ktor.server.application.*

/**
 * Represents a single entry in the [RoutingResolveTrace].
 * @param route specifies instance of [RouteNode] for this entry.
 * @param segmentIndex specifies index in [RoutingResolveTrace.segments] for this entry.
 * @param result specifies resolution result for this entry.
 */
public open class RoutingResolveTraceEntry(
    public val route: RouteNode,
    public val segmentIndex: Int,
    public var result: RoutingResolveResult? = null
) {
    /**
     * Optional list of children registered for this entry, or null if no children were processed.
     */
    private var children: MutableList<RoutingResolveTraceEntry>? = null

    /**
     * Appends a child to this entry
     */
    public fun append(item: RoutingResolveTraceEntry) {
        val items = children ?: mutableListOf<RoutingResolveTraceEntry>().also { children = it }
        items.add(item)
    }

    /**
     * Builds detailed text description for this trace entry, including children.
     */
    public open fun buildText(builder: StringBuilder, indent: Int) {
        builder.appendLine("  ".repeat(indent) + toString())
        children?.forEach { it.buildText(builder, indent + 1) }
    }

    override fun toString(): String = "$route, segment:$segmentIndex -> $result"
}

/**
 * Represents the trace of routing resolution process for diagnostics.
 * @param call instance of [PipelineCall] for which this trace was created.
 * @param segments list of [String]s for each path segment supplied for the routing resolution.
 */
public class RoutingResolveTrace(public val call: PipelineCall, public val segments: List<String>) {
    private val stack = Stack<RoutingResolveTraceEntry>()
    private var routing: RoutingResolveTraceEntry? = null
    private lateinit var finalResult: RoutingResolveResult
    private val resolveCandidates: MutableList<List<RoutingResolveResult.Success>> = mutableListOf()

    private fun register(entry: RoutingResolveTraceEntry) {
        if (stack.empty()) {
            routing = entry
        } else {
            stack.peek().append(entry)
        }
    }

    /**
     * Begins processing a [route] at segment with [segmentIndex] in [segments].
     */
    public fun begin(route: RouteNode, segmentIndex: Int) {
        stack.push(RoutingResolveTraceEntry(route, segmentIndex))
    }

    /**
     * Finishes processing a [route] at segment with [segmentIndex] in [segments] with the given [result].
     */
    public fun finish(route: RouteNode, segmentIndex: Int, result: RoutingResolveResult) {
        val entry = stack.pop()
        require(entry.route == route) { "end should be called for the same route as begin" }
        require(entry.segmentIndex == segmentIndex) { "end should be called for the same segmentIndex as begin" }
        entry.result = result
        register(entry)
    }

    /**
     * Begins and finishes processing a [route] at segment with [segmentIndex] in [segments] with the given [result].
     */
    public fun skip(route: RouteNode, segmentIndex: Int, result: RoutingResolveResult) {
        register(RoutingResolveTraceEntry(route, segmentIndex, result))
    }

    public fun registerFinalResult(result: RoutingResolveResult) {
        this.finalResult = result
    }

    override fun toString(): String = "Trace for $segments"

    /**
     * Builds detailed text description for this trace, including all entries.
     */
    public fun buildText(): String = buildString {
        appendLine(this@RoutingResolveTrace.toString())
        routing?.buildText(this, 0)
        if (!this@RoutingResolveTrace::finalResult.isInitialized) {
            return@buildString
        }
        appendLine("Matched routes:")
        if (resolveCandidates.isEmpty()) {
            appendLine("  No results")
        } else {
            appendLine(
                resolveCandidates.joinToString("\n") { path ->
                    path.joinToString(" -> ", prefix = "  ") {
                        """"${it.route.selector}""""
                    }
                }
            )
        }
        appendLine("Route resolve result:")
        append("  $finalResult")
    }

    /**
     * Add candidate for resolving.
     */
    public fun addCandidate(trait: List<RoutingResolveResult.Success>) {
        val candidate = List(trait.size) { trait[it] }
        resolveCandidates.add(candidate)
    }
}

private class Stack<E> {
    private val tower = ArrayList<E>()

    fun empty(): Boolean = tower.isEmpty()

    fun push(element: E) {
        tower.add(element)
    }

    fun pop(): E {
        if (tower.isEmpty()) {
            throw NoSuchElementException("Unable to pop an element from empty stack")
        }
        return tower.removeAt(tower.lastIndex)
    }

    fun peek(): E {
        if (tower.isEmpty()) {
            throw NoSuchElementException("Unable to peek an element into empty stack")
        }
        return tower.last()
    }
}
