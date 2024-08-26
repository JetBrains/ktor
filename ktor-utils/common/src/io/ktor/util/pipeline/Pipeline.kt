/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import io.ktor.util.*
import io.ktor.util.debug.*
import kotlinx.atomicfu.*
import kotlin.coroutines.*

// helper interface for `startInterceptorCoroutineUninterceptedOrReturn`
internal typealias PipelineInterceptorCoroutine<TSubject, TContext> =
    (PipelineContext<TSubject, TContext>, TSubject, Continuation<Unit>) -> Any?

// Overall, it does the same as `startCoroutineUninterceptedOrReturn` from stdlib.
// Stdlib even has `(suspend R.(P) -> T).startCoroutineUninterceptedOrReturn`, but it's internal.
// If it was public, then this function would be just:
// `interceptor.startCoroutineUninterceptedOrReturn(context, subject, continuation)`
internal expect fun <TSubject : Any, TContext : Any> pipelineStartCoroutineUninterceptedOrReturn(
    interceptor: PipelineInterceptor<TSubject, TContext>,
    context: PipelineContext<TSubject, TContext>,
    subject: TSubject,
    continuation: Continuation<Unit>
): Any?

/**
 * Represents an execution pipeline for asynchronous extensible computations
 */

public open class Pipeline<TSubject : Any, TContext : Any>(
    vararg phases: PipelinePhase
) {
    /**
     * Provides common place to store pipeline attributes
     */
    public val attributes: Attributes = Attributes(concurrent = true)

    /**
     * Indicated if debug mode is enabled. In debug mode users will get more details in the stacktrace.
     */
    public open val developmentMode: Boolean = false

    private val phasesRaw: MutableList<Any> = mutableListOf(*phases)

    private var interceptorsQuantity = 0

    /**
     * Phases of this pipeline
     */
    public val items: List<PipelinePhase>
        get() = phasesRaw.map {
            it as? PipelinePhase ?: (it as? PhaseContent<*, *>)?.phase!!
        }

    /**
     * @return `true` if there are no interceptors installed regardless number of phases
     */
    public val isEmpty: Boolean
        get() = interceptorsQuantity == 0

    private val _interceptors: AtomicRef<List<PipelineInterceptor<TSubject, TContext>>?> =
        atomic(null)

    private var interceptors: List<PipelineInterceptor<TSubject, TContext>>?
        get() = _interceptors.value
        set(value) {
            _interceptors.value = value
        }

    private var interceptorsListShared: Boolean = false

    private var interceptorsListSharedPhase: PipelinePhase? = null

    public constructor(
        phase: PipelinePhase,
        interceptors: List<PipelineInterceptor<TSubject, TContext>>
    ) : this(phase) {
        interceptors.forEach { intercept(phase, it) }
    }

    /**
     * Executes this pipeline in the given [context] and with the given [subject]
     */
    public suspend fun execute(context: TContext, subject: TSubject): TSubject =
        createContext(context, subject, coroutineContext).execute(subject)

    /**
     * Adds [phase] to the end of this pipeline
     */
    public fun addPhase(phase: PipelinePhase) {
        if (hasPhase(phase)) {
            return
        }

        phasesRaw.add(phase)
    }

    /**
     * Inserts [phase] after the [reference] phase. If there are other phases inserted after [reference], then [phase]
     * will be inserted after them.
     * Example:
     * ```
     * val pipeline = Pipeline<String, String>(a)
     * pipeline.insertPhaseAfter(a, b)
     * pipeline.insertPhaseAfter(a, c)
     * assertEquals(listOf(a, b, c), pipeline.items)
     * ```
     */
    public fun insertPhaseAfter(reference: PipelinePhase, phase: PipelinePhase) {
        if (hasPhase(phase)) return

        val index = findPhaseIndex(reference)
        if (index == -1) {
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        }
        // insert after the last phase that has Relation.After on [reference]
        var lastRelatedPhaseIndex = index
        for (i in index + 1..phasesRaw.lastIndex) {
            val relation = (phasesRaw[i] as? PhaseContent<*, *>)?.relation ?: break
            val relatedTo = (relation as? PipelinePhaseRelation.After)?.relativeTo ?: continue
            lastRelatedPhaseIndex = if (relatedTo == reference) i else lastRelatedPhaseIndex
        }

        phasesRaw.add(
            lastRelatedPhaseIndex + 1,
            PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.After(reference))
        )
    }

    /**
     * Inserts [phase] before the [reference] phase.
     * Example:
     * ```
     * val pipeline = Pipeline<String, String>(c)
     * pipeline.insertPhaseBefore(c, a)
     * pipeline.insertPhaseBefore(c, b)
     * assertEquals(listOf(a, b, c), pipeline.items)
     * ```
     */
    public fun insertPhaseBefore(reference: PipelinePhase, phase: PipelinePhase) {
        if (hasPhase(phase)) return

        val index = findPhaseIndex(reference)
        if (index == -1) {
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        }

        phasesRaw.add(index, PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.Before(reference)))
    }

    /**
     * Adds [block] to the [phase] of this pipeline
     */
    public fun intercept(phase: PipelinePhase, block: PipelineInterceptor<TSubject, TContext>) {
        val phaseContent = findPhase(phase)
            ?: throw InvalidPhaseException("Phase $phase was not registered for this pipeline")

        if (tryAddToPhaseFastPath(phase, block)) {
            interceptorsQuantity++
            return
        }

        phaseContent.addInterceptor(block)
        interceptorsQuantity++
        resetInterceptorsList()

        afterIntercepted()
    }

    /**
     * Invoked after an interceptor has been installed
     */
    public open fun afterIntercepted() {
    }

    public fun interceptorsForPhase(phase: PipelinePhase): List<PipelineInterceptor<TSubject, TContext>> {
        @Suppress("UNCHECKED_CAST")
        return phasesRaw.filterIsInstance<PhaseContent<*, *>>()
            .firstOrNull { phaseOrContent -> phaseOrContent.phase == phase }
            ?.sharedInterceptors() as List<PipelineInterceptor<TSubject, TContext>>?
            ?: emptyList()
    }

    public fun mergePhases(from: Pipeline<TSubject, TContext>) {
        val fromPhases = from.phasesRaw
        val toInsert = fromPhases.toMutableList()
        // the worst case is O(n^2), but it will happen only
        // when all phases were inserted before each other into the second pipeline
        // (see test testDependantPhasesLastCommon).
        // in practice, it will be linear time for most cases
        while (toInsert.isNotEmpty()) {
            val iterator = toInsert.iterator()
            while (iterator.hasNext()) {
                val fromPhaseOrContent = iterator.next()

                val fromPhase = (fromPhaseOrContent as? PipelinePhase)
                    ?: (fromPhaseOrContent as PhaseContent<*, *>).phase

                if (hasPhase(fromPhase)) {
                    iterator.remove()
                } else {
                    val inserted = insertRelativePhase(fromPhaseOrContent, fromPhase)
                    if (inserted) {
                        iterator.remove()
                    }
                }
            }
        }
    }

    private fun mergeInterceptors(from: Pipeline<TSubject, TContext>) {
        if (interceptorsQuantity == 0) {
            setInterceptorsListFromAnotherPipeline(from)
        } else {
            resetInterceptorsList()
        }

        val fromPhases = from.phasesRaw
        fromPhases.forEach { fromPhaseOrContent ->
            val fromPhase = (fromPhaseOrContent as? PipelinePhase)
                ?: (fromPhaseOrContent as PhaseContent<*, *>).phase

            if (fromPhaseOrContent is PhaseContent<*, *> && !fromPhaseOrContent.isEmpty) {
                @Suppress("UNCHECKED_CAST")
                fromPhaseOrContent as PhaseContent<TSubject, TContext>

                fromPhaseOrContent.addTo(findPhase(fromPhase)!!)
                interceptorsQuantity += fromPhaseOrContent.size
            }
        }
    }

    /**
     * Merges another pipeline into this pipeline, maintaining relative phases order
     */
    public fun merge(from: Pipeline<TSubject, TContext>) {
        if (fastPathMerge(from)) {
            return
        }

        mergePhases(from)
        mergeInterceptors(from)
    }

    /**
     * Reset current pipeline from other.
     */
    public fun resetFrom(from: Pipeline<TSubject, TContext>) {
        phasesRaw.clear()
        check(interceptorsQuantity == 0)

        fastPathMerge(from)
    }

    override fun toString(): String {
        val interceptors = interceptorsForTests()
            .joinToString("\n") { "    " + it::class.toString() }

        return "${this::class}(0x${hashCode().toString(16)}) [\n$interceptors\n]"
    }

    internal fun phaseInterceptors(phase: PipelinePhase): List<PipelineInterceptor<TSubject, TContext>> =
        findPhase(phase)?.sharedInterceptors() ?: emptyList()

    /**
     * For tests only
     */
    internal fun interceptorsForTests(): List<PipelineInterceptor<TSubject, TContext>> {
        return interceptors ?: cacheInterceptors()
    }

    private fun createContext(
        context: TContext,
        subject: TSubject,
        coroutineContext: CoroutineContext
    ): PipelineContext<TSubject, TContext> =
        pipelineContextFor(context, sharedInterceptorsList(), subject, coroutineContext, developmentMode)

    private fun findPhase(phase: PipelinePhase): PhaseContent<TSubject, TContext>? {
        val phasesList = phasesRaw

        for (index in 0 until phasesList.size) {
            val current = phasesList[index]
            if (current === phase) {
                val content = PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.Last)
                phasesList[index] = content
                return content
            }

            if (current is PhaseContent<*, *> && current.phase === phase) {
                @Suppress("UNCHECKED_CAST")
                return current as PhaseContent<TSubject, TContext>
            }
        }

        return null
    }

    private fun findPhaseIndex(phase: PipelinePhase): Int {
        val phasesList = phasesRaw
        for (index in 0 until phasesList.size) {
            val current = phasesList[index]
            if (current === phase || (current is PhaseContent<*, *> && current.phase === phase)) {
                return index
            }
        }

        return -1
    }

    private fun hasPhase(phase: PipelinePhase): Boolean {
        val phasesList = phasesRaw
        for (index in 0 until phasesList.size) {
            val current = phasesList[index]
            if (current === phase || (current is PhaseContent<*, *> && current.phase === phase)) {
                return true
            }
        }

        return false
    }

    private fun cacheInterceptors(): List<PipelineInterceptor<TSubject, TContext>> {
        val interceptorsQuantity = interceptorsQuantity
        if (interceptorsQuantity == 0) {
            notSharedInterceptorsList(emptyList())
            return emptyList()
        }

        val phases = phasesRaw
        if (interceptorsQuantity == 1) {
            for (phaseIndex in 0..phases.lastIndex) {
                @Suppress("UNCHECKED_CAST")
                val phaseContent =
                    phases[phaseIndex] as? PhaseContent<TSubject, TContext> ?: continue

                if (phaseContent.isEmpty) continue

                val interceptors = phaseContent.sharedInterceptors()
                setInterceptorsListFromPhase(phaseContent)
                return interceptors
            }
        }

        val destination: MutableList<PipelineInterceptor<TSubject, TContext>> = mutableListOf()
        for (phaseIndex in 0..phases.lastIndex) {
            @Suppress("UNCHECKED_CAST")
            val phase = phases[phaseIndex] as? PhaseContent<TSubject, TContext> ?: continue

            phase.addTo(destination)
        }

        notSharedInterceptorsList(destination)
        return destination
    }

    private fun fastPathMerge(from: Pipeline<TSubject, TContext>): Boolean {
        if (from.phasesRaw.isEmpty()) {
            return true
        }

        if (phasesRaw.isNotEmpty()) {
            return false
        }

        val fromPhases = from.phasesRaw

        for (index in 0..fromPhases.lastIndex) {
            val fromPhaseOrContent = fromPhases[index]
            if (fromPhaseOrContent is PipelinePhase) {
                phasesRaw.add(fromPhaseOrContent)
                continue
            }

            if (fromPhaseOrContent !is PhaseContent<*, *>) {
                continue
            }

            @Suppress("UNCHECKED_CAST")
            fromPhaseOrContent as PhaseContent<TSubject, TContext>

            phasesRaw.add(
                PhaseContent(
                    fromPhaseOrContent.phase,
                    fromPhaseOrContent.relation,
                    fromPhaseOrContent.sharedInterceptors()
                )
            )
            continue
        }

        interceptorsQuantity += from.interceptorsQuantity
        setInterceptorsListFromAnotherPipeline(from)
        return true
    }

    private fun sharedInterceptorsList(): List<PipelineInterceptor<TSubject, TContext>> {
        if (interceptors == null) {
            cacheInterceptors()
        }

        interceptorsListShared = true
        return interceptors!!
    }

    private fun resetInterceptorsList() {
        interceptors = null
        interceptorsListShared = false
        interceptorsListSharedPhase = null
    }

    private fun notSharedInterceptorsList(list: List<PipelineInterceptor<TSubject, TContext>>) {
        interceptors = list
        interceptorsListShared = false
        interceptorsListSharedPhase = null
    }

    private fun setInterceptorsListFromPhase(phaseContent: PhaseContent<TSubject, TContext>) {
        interceptors = phaseContent.sharedInterceptors()
        interceptorsListShared = false
        interceptorsListSharedPhase = phaseContent.phase
    }

    private fun setInterceptorsListFromAnotherPipeline(pipeline: Pipeline<TSubject, TContext>) {
        interceptors = pipeline.sharedInterceptorsList()
        interceptorsListShared = true
        interceptorsListSharedPhase = null
    }

    private fun tryAddToPhaseFastPath(
        phase: PipelinePhase,
        block: PipelineInterceptor<TSubject, TContext>
    ): Boolean {
        val currentInterceptors = interceptors
        if (phasesRaw.isEmpty() || currentInterceptors == null) {
            return false
        }

        if (interceptorsListShared || currentInterceptors !is MutableList) {
            return false
        }

        if (interceptorsListSharedPhase == phase) {
            currentInterceptors.add(block)
            return true
        }

        if (phase == phasesRaw.last() || findPhaseIndex(phase) == phasesRaw.lastIndex) {
            findPhase(phase)!!.addInterceptor(block)
            currentInterceptors.add(block)
            return true
        }

        return false
    }

    private fun insertRelativePhase(fromPhaseOrContent: Any, fromPhase: PipelinePhase): Boolean {
        val fromPhaseRelation = when {
            fromPhaseOrContent === fromPhase -> PipelinePhaseRelation.Last
            else -> (fromPhaseOrContent as PhaseContent<*, *>).relation
        }

        when {
            fromPhaseRelation is PipelinePhaseRelation.Last ->
                addPhase(fromPhase)

            fromPhaseRelation is PipelinePhaseRelation.Before && hasPhase(fromPhaseRelation.relativeTo) ->
                insertPhaseBefore(fromPhaseRelation.relativeTo, fromPhase)

            fromPhaseRelation is PipelinePhaseRelation.After ->
                insertPhaseAfter(fromPhaseRelation.relativeTo, fromPhase)

            else -> return false
        }
        return true
    }
}

/**
 * Executes this pipeline
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun <TContext : Any> Pipeline<Unit, TContext>.execute(
    context: TContext
) {
    // A list of executed plugins with their handlers must be attached to the call's coroutine context
    // in order to be available from the IntelliJ debugger any time inside the call.
    initContextInDebugMode {
        execute(context, Unit)
    }
}

/**
 * Intercepts an untyped pipeline when the subject is of the given type
 */
public inline fun <reified TSubject : Any, TContext : Any> Pipeline<*, TContext>.intercept(
    phase: PipelinePhase,
    noinline block: suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit
) {
    intercept(phase) interceptor@{ subject ->
        if (subject !is TSubject) return@interceptor

        @Suppress("UNCHECKED_CAST")
        val reinterpret = this as? PipelineContext<TSubject, TContext>
        reinterpret?.block(subject)
    }
}

/**
 * Represents an interceptor type which is a suspend extension function for a context
 */
public typealias PipelineInterceptor<TSubject, TContext> =
    suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit
