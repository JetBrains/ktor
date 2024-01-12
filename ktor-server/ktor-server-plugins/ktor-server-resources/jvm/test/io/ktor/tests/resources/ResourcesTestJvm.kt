/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.resources
import io.ktor.resources.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.math.*
import kotlin.test.*

class ResourcesTestJvm {

    object BigDecimalSerializer : KSerializer<BigDecimal> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BigDecimal) {
            val string = value.toString()
            encoder.encodeString(string)
        }

        override fun deserialize(decoder: Decoder): BigDecimal {
            val string = decoder.decodeString()
            return BigDecimal(string)
        }
    }

    @Resource("/")
    class LocationWithBigNumbers(
        @Serializable(with = BigDecimalSerializer::class)
        val bd: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        val bi: BigDecimal
    )

    @Test
    fun locationClassWithBigNumbers() = withResourcesApplication {
        val bd = BigDecimal("123456789012345678901234567890")
        val bi = BigDecimal("123456789012345678901234567890")

        routing {
            get<LocationWithBigNumbers> { location ->
                assertEquals(bd, location.bd)
                assertEquals(bi, location.bi)

                call.respondText(application.href(location))
            }
        }

        urlShouldBeHandled(
            LocationWithBigNumbers(bd, bi),
            "/?bd=123456789012345678901234567890&bi=123456789012345678901234567890"
        )
    }
}
