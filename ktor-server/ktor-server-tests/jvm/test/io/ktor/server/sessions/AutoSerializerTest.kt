/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.http.*
import java.math.*
import java.util.*
import kotlin.test.*

class AutoSerializerTest {
    @Test
    fun testSimple() {
        val serializer = reflectionSessionSerializer<TestSession>()
        val result = serializer.deserialize("test=#i1&list=#cl${"#sa&#sb&#sc".encodeURLParameter()}")

        assertEquals(1, result.test)
        assertEquals(listOf("a", "b", "c"), result.list)

        assertSerializeDeserialize(result, serializer)
    }

    @Test
    fun testPrimitive() {
        assertSerializeDeserialize(PrimitiveSession(), reflectionSessionSerializer())
    }

    @Test
    fun testLists() {
        assertSerializeDeserialize(ListSession(), reflectionSessionSerializer())
    }

    @Test
    fun testSets() {
        assertSerializeDeserialize(SetSession(), reflectionSessionSerializer())
    }

    @Test
    fun testMaps() {
        assertSerializeDeserialize(MapSession(), reflectionSessionSerializer())
    }

    @Test
    fun testAdditionalTypes() {
        assertSerializeDeserialize(AdditionalTypesSession(), reflectionSessionSerializer())
        // TODO randomize values
    }

    @Test
    fun testEnum() {
        assertSerializeDeserialize(EnumTypeSession(), reflectionSessionSerializer())
    }

    @Test
    fun testEnumInCollection() {
        assertSerializeDeserialize(EnumCollectionSession(), reflectionSessionSerializer())
    }

    @Test
    fun testCompoundClasses() {
        assertSerializeDeserialize(CompoundSession(), reflectionSessionSerializer())
    }

    @Test
    fun testSealedSession() {
        assertSerializeDeserialize(SealedSession.SS(), reflectionSessionSerializer())
        assertSerializeDeserialize(SealedSession.SS(), reflectionSessionSerializer<SealedSession>())
    }

    @Test
    fun testSealedSessionObject() {
        assertSerializeDeserialize(SealedSession.E, reflectionSessionSerializer())
        assertSerializeDeserialize(SealedSession.E, reflectionSessionSerializer<SealedSession>())
    }

    @Test
    fun testSessionWithSealedMember() {
        assertSerializeDeserialize(SessionWithSealedMember(), reflectionSessionSerializer())
    }

    private fun <T : Any> assertSerializeDeserialize(session: T, serializer: SessionSerializer<T>) {
        val serialized = serializer.serialize(session)
        val deserialized = serializer.deserialize(serialized)
        assertEquals(session, deserialized)
    }
}

data class TestSession(
    var test: Int = 1,
    var list: List<String> = listOf("z")
)

data class PrimitiveSession(
    var i: Int = 1,
    var s: String = "sss",
    var l: Long = 2L,
    var b1: Boolean = true,
    var b2: Boolean = false,
    var ch: Char = 'a',
    var f: Float = 1.0f,
    var d: Double = 1.0
)

data class ListSession(
    var mutable1: List<String> = listOf("a", "b"),
    var mutable: LinkedList<String> = LinkedList(listOf("a")),
    val immutable: ArrayList<String> = arrayListOf("a", "b")
)

data class SetSession(
    var mutable1: Set<String> = setOf("a", "b"),
    var mutable: TreeSet<String> = TreeSet(setOf("a")),
    val immutable: HashSet<String> = hashSetOf("a", "b")
)

data class MapSession(
    var mutable1: Map<String, String> = mapOf("a" to "b"),
    var mutable: TreeMap<String, String> = TreeMap(mapOf("a" to "b")),
    val immutable: HashMap<String, String> = hashMapOf("a" to "b")
)

data class AdditionalTypesSession(
    val optionalEmpty: Optional<String> = Optional.empty(),
    val optionWithContent: Optional<String> = Optional.of("a"),
    var bd: BigDecimal = BigDecimal.ZERO,
    var bi: BigInteger = BigInteger.TEN,
    var uuid: UUID = UUID.randomUUID()
)

enum class TestEnum { A, B, C }
data class EnumTypeSession(val e: TestEnum = TestEnum.B)
data class EnumCollectionSession(
    val ll: List<TestEnum> = listOf(TestEnum.B),
    val ss: Set<TestEnum> = setOf(TestEnum.A),
    val mm: Map<TestEnum, TestEnum> = mapOf(TestEnum.A to TestEnum.B)
)

data class Part(val i: Int)
data class CompoundSession(val part: Part = Part(779))

sealed class SealedSession {
    data class SS(val e: Int = 834) : SealedSession()
    data object E : SealedSession()
}

data class SessionWithSealedMember(val member: SealedSession = SealedSession.SS())
