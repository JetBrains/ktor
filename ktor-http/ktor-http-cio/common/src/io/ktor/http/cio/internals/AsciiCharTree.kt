/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.internals

internal class AsciiCharTree<T : Any>(val root: Node<T>) {
    class Node<T>(val ch: Char, val exact: List<T>, val children: List<Node<T>>) {
        val array = Array(0x100) { chi -> children.singleOrNull { it.ch.code == chi } }
    }

    fun search(
        sequence: CharSequence,
        fromIdx: Int = 0,
        end: Int = sequence.length,
        lowerCase: Boolean = false,
        stopPredicate: (Char, Int) -> Boolean
    ): List<T> {
        if (sequence.isEmpty()) throw IllegalArgumentException("Couldn't search in char tree for empty string")
        var node = root

        for (index in fromIdx until end) {
            val current = sequence[index]
            val currentCode = current.code

            if (stopPredicate(current, currentCode)) break

            val nextNode = node.array[currentCode]
                ?: (if (lowerCase) node.array[current.lowercaseChar().code] else null)
                ?: return emptyList()

            node = nextNode
        }

        return node.exact
    }

    companion object {
        fun <T : CharSequence> build(from: List<T>): AsciiCharTree<T> {
            return build(from, { it.length }, { s, idx -> s[idx] })
        }

        fun <T : Any> build(from: List<T>, length: (T) -> Int, charAt: (T, Int) -> Char): AsciiCharTree<T> {
            val maxLen = from.maxByOrNull(length)?.let(length)
                ?: throw NoSuchElementException("Unable to build char tree from an empty list")

            if (from.any { length(it) == 0 }) throw IllegalArgumentException("There should be no empty entries")

            val root = ArrayList<Node<T>>()
            build(root, from, maxLen, 0, length, charAt)
            root.trimToSize()
            return AsciiCharTree(Node('\u0000', emptyList(), root))
        }

        private fun <T : Any> build(
            resultList: MutableList<Node<T>>,
            from: List<T>,
            maxLength: Int,
            idx: Int,
            length: (T) -> Int,
            charAt: (T, Int) -> Char
        ) {
            from.groupBy { charAt(it, idx) }.forEach { (ch, list) ->
                val nextIdx = idx + 1
                val children = ArrayList<Node<T>>()
                build(children, list.filter { length(it) > nextIdx }, maxLength, nextIdx, length, charAt)
                children.trimToSize()
                resultList.add(Node(ch, list.filter { length(it) == nextIdx }, children))
            }
        }
    }
}
