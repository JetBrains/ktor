/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

@Suppress("DEPRECATION")
internal class PhaseContent<TSubject : Any, Call : Any>(
    val phase: PipelinePhase,
    val relation: PipelinePhaseRelation,
    interceptors: MutableList<PipelineInterceptorFunction<TSubject, Call>>
) {
    private var interceptors: MutableList<PipelineInterceptorFunction<TSubject, Call>> = interceptors

    @Suppress("UNCHECKED_CAST")
    constructor(
        phase: PipelinePhase,
        relation: PipelinePhaseRelation
    ) : this(phase, relation, SharedArrayList as MutableList<PipelineInterceptorFunction<TSubject, Call>>) {
        check(SharedArrayList.isEmpty()) { "The shared empty array list has been modified" }
    }

    var shared: Boolean = true

    val isEmpty: Boolean get() = interceptors.isEmpty()
    val size: Int get() = interceptors.size

    fun addInterceptor(interceptor: PipelineInterceptorFunction<TSubject, Call>) {
        if (shared) {
            copyInterceptors()
        }

        interceptors.add(interceptor)
    }

    fun addTo(destination: MutableList<PipelineInterceptorFunction<TSubject, Call>>) {
        val interceptors = interceptors

        if (destination is ArrayList) {
            destination.ensureCapacity(destination.size + interceptors.size)
        }

        for (index in 0 until interceptors.size) {
            destination.add(interceptors[index])
        }
    }

    fun addTo(destination: PhaseContent<TSubject, Call>) {
        if (isEmpty) return

        if (destination.isEmpty) {
            destination.interceptors = sharedInterceptors()
            destination.shared = true
            return
        }

        if (destination.shared) {
            destination.copyInterceptors()
        }

        addTo(destination.interceptors)
    }

    fun sharedInterceptors(): MutableList<PipelineInterceptorFunction<TSubject, Call>> {
        shared = true
        return interceptors
    }

    fun copiedInterceptors(): MutableList<PipelineInterceptorFunction<TSubject, Call>> =
        mutableListOf<PipelineInterceptorFunction<TSubject, Call>>().apply {
            addAll(interceptors)
        }

    override fun toString(): String = "Phase `${phase.name}`, $size handlers"

    private fun copyInterceptors() {
        interceptors = copiedInterceptors()
        shared = false
    }

    companion object {
        val SharedArrayList: MutableList<Any?> = mutableListOf()
    }
}
