/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.internals

import io.ktor.utils.io.pool.*
import kotlin.math.*

internal class CharArrayBuilder(
    val pool: ObjectPool<CharArray> = CharArrayPool
) : CharSequence, Appendable {

    private var buffers: MutableList<CharArray>? = null
    private var current: CharArray? = null
    private var stringified: String? = null
    private var released: Boolean = false
    private var remaining: Int = 0

    override var length: Int = 0
        private set

    override fun get(index: Int): Char {
        require(index >= 0) { "index is negative: $index" }
        require(index < length) { "index $index is not in range [0, $length)" }

        return getImpl(index)
    }

    private fun getImpl(index: Int) = bufferForIndex(index)[index % current!!.size]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex <= endIndex) { "startIndex ($startIndex) should be less or equal to endIndex ($endIndex)" }
        require(startIndex >= 0) { "startIndex is negative: $startIndex" }
        require(endIndex <= length) { "endIndex ($endIndex) is greater than length ($length)" }

        return SubSequenceImpl(startIndex, endIndex)
    }

    override fun toString(): String = stringified ?: copy(0, length).toString().also { stringified = it }

    override fun equals(other: Any?): Boolean {
        if (other !is CharSequence) return false
        if (length != other.length) return false

        return rangeEqualsImpl(0, other, 0, length)
    }

    override fun hashCode(): Int = stringified?.hashCode() ?: hashCodeImpl(0, length)

    override fun append(value: Char): Appendable {
        nonFullBuffer()[current!!.size - remaining] = value
        stringified = null
        remaining -= 1
        length++
        return this
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        value ?: return this

        var current = startIndex
        while (current < endIndex) {
            val buffer = nonFullBuffer()
            val offset = buffer.size - remaining
            val bytesToCopy = min(endIndex - current, remaining)

            for (i in 0 until bytesToCopy) {
                buffer[offset + i] = value[current + i]
            }

            current += bytesToCopy
            remaining -= bytesToCopy
        }

        stringified = null
        length += endIndex - startIndex
        return this
    }

    override fun append(value: CharSequence?): Appendable {
        value ?: return this
        return append(value, 0, value.length)
    }

    fun release() {
        val list = buffers

        if (list != null) {
            current = null
            for (i in 0 until list.size) {
                pool.recycle(list[i])
            }
        } else {
            current?.let { pool.recycle(it) }
            current = null
        }

        released = true
        buffers = null
        stringified = null
        length = 0
        remaining = 0
    }

    private fun copy(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex == endIndex) return ""

        val builder = StringBuilder(endIndex - startIndex)

        var buffer: CharArray

        var base = startIndex - (startIndex % CHAR_BUFFER_ARRAY_LENGTH)

        while (base < endIndex) {
            buffer = bufferForIndex(base)
            val innerStartIndex = maxOf(0, startIndex - base)
            val innerEndIndex = minOf(endIndex - base, CHAR_BUFFER_ARRAY_LENGTH)

            for (innerIndex in innerStartIndex until innerEndIndex) {
                builder.append(buffer[innerIndex])
            }

            base += CHAR_BUFFER_ARRAY_LENGTH
        }

        return builder
    }

    @Suppress("ConvertTwoComparisonsToRangeCheck")
    private inner class SubSequenceImpl(val start: Int, val end: Int) : CharSequence {
        private var stringified: String? = null

        override val length: Int
            get() = end - start

        override fun get(index: Int): Char {
            val withOffset = index + start
            require(index >= 0) { "index is negative: $index" }
            require(withOffset < end) { "index ($index) should be less than length ($length)" }

            return this@CharArrayBuilder.getImpl(withOffset)
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            require(startIndex >= 0) { "start is negative: $startIndex" }
            require(startIndex <= endIndex) { "start ($startIndex) should be less or equal to end ($endIndex)" }
            require(endIndex <= end - start) { "end should be less than length ($length)" }
            if (startIndex == endIndex) return ""

            return SubSequenceImpl(start + startIndex, start + endIndex)
        }

        override fun toString() = stringified ?: copy(start, end).toString().also { stringified = it }

        override fun equals(other: Any?): Boolean {
            if (other !is CharSequence) return false
            if (other.length != length) return false

            return rangeEqualsImpl(start, other, 0, length)
        }

        override fun hashCode() = stringified?.hashCode() ?: hashCodeImpl(start, end)
    }

    private fun bufferForIndex(index: Int): CharArray {
        val list = buffers

        if (list == null) {
            if (index >= CHAR_BUFFER_ARRAY_LENGTH) throwSingleBuffer(index)
            return current ?: throwSingleBuffer(index)
        }

        return list[index / current!!.size]
    }

    private fun throwSingleBuffer(index: Int): Nothing {
        if (released) throw IllegalStateException("Buffer is already released")
        throw IndexOutOfBoundsException("$index is not in range [0; ${currentPosition()})")
    }

    private fun nonFullBuffer(): CharArray {
        return if (remaining == 0) appendNewArray() else current!!
    }

    private fun appendNewArray(): CharArray {
        val newBuffer = pool.borrow()
        val existing = current
        current = newBuffer
        remaining = newBuffer.size

        released = false

        if (existing != null) {
            val list = buffers ?: ArrayList<CharArray>().also {
                buffers = it
                it.add(existing)
            }

            list.add(newBuffer)
        }

        return newBuffer
    }

    private fun rangeEqualsImpl(start: Int, other: CharSequence, otherStart: Int, length: Int): Boolean {
        for (i in 0 until length) {
            if (getImpl(start + i) != other[otherStart + i]) return false
        }

        return true
    }

    private fun hashCodeImpl(start: Int, end: Int): Int {
        var hc = 0
        for (i in start until end) {
            hc = 31 * hc + getImpl(i).code
        }

        return hc
    }

    private fun currentPosition() = current!!.size - remaining
}
