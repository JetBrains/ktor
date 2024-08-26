/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.serialization.gson

import com.google.gson.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * A content converter that uses [Gson]
 *
 * @param gson a configured instance of [Gson]
 */
public class GsonConverter(private val gson: Gson = Gson()) : ContentConverter {

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        // specific behavior for kotlinx.coroutines.flow.Flow
        if (typeInfo.type == Flow::class) {
            return OutputStreamContent(
                {
                    val writer = this.writer(charset = charset)
                    // emit asynchronous values in Writer without pretty print
                    (value as Flow<*>).serializeJson(writer)
                },
                contentType.withCharsetIfNeeded(charset)
            )
        }
        return TextContent(gson.toJson(value), contentType.withCharsetIfNeeded(charset))
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        if (gson.isExcluded(typeInfo.type)) {
            throw ExcludedTypeGsonException(typeInfo.type)
        }

        try {
            return withContext(Dispatchers.IO) {
                val reader = content.toInputStream().reader(charset)
                gson.fromJson(reader, typeInfo.reifiedType)
            }
        } catch (cause: JsonSyntaxException) {
            throw JsonConvertException("Illegal json parameter found: ${cause.message}", cause)
        }
    }

    private companion object {
        private const val beginArrayCharCode = '['.code
        private const val endArrayCharCode = ']'.code
        private const val objectSeparator = ','.code
    }

    /**
     * Guaranteed to be called inside a [Dispatchers.IO] context, see [OutputStreamContent]
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun <T> Flow<T>.serializeJson(writer: Writer) {
        writer.write(beginArrayCharCode)
        collectIndexed { index, value ->
            if (index > 0) {
                writer.write(objectSeparator)
            }
            gson.toJson(value, writer)
            writer.flush()
        }
        writer.write(endArrayCharCode)
        writer.flush()
    }
}

internal fun Gson.isExcluded(type: KClass<*>) =
    excluder().excludeClass(type.java, false)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExcludedTypeGsonException(
    private val type: KClass<*>
) : Exception("Type ${type.jvmName} is excluded so couldn't be used in receive"),
    CopyableThrowable<ExcludedTypeGsonException> {

    override fun createCopy(): ExcludedTypeGsonException = ExcludedTypeGsonException(type).also {
        it.initCause(this)
    }
}

/**
 * Registers the `application/json` content type to the [ContentNegotiation] plugin using GSON.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 */
public fun Configuration.gson(
    contentType: ContentType = ContentType.Application.Json,
    block: GsonBuilder.() -> Unit = {}
) {
    val builder = GsonBuilder()
    builder.apply(block)
    val converter = GsonConverter(builder.create())
    register(contentType, converter)
}
