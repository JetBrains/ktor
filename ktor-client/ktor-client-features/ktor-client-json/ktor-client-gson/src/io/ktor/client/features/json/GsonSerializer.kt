package io.ktor.client.features.json

import com.google.gson.*
import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.content.*
import io.ktor.http.*


/**
 * [JsonSerializer] using [Gson] as backend.
 */
class GsonSerializer(block: GsonBuilder.() -> Unit = {}) : JsonSerializer {

    private val backend: Gson = GsonBuilder().apply(block).create()

    override fun write(data: Any): OutgoingContent = TextContent(backend.toJson(data), ContentType.Application.Json)

    override suspend fun read(type: TypeInfo, response: HttpResponse): Any {
        val text= response.readText()
        return backend.fromJson(text, type.reifiedType)
    }
}
