package io.ktor.tests.serializable

import io.ktor.http.*
import io.ktor.serializable.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*

class SerializableJsonTest : SerializableTest() {
    override val contentType = ContentType.Application.Json
    override val contentConverter = SerializableJsonConverter()

    override fun parseResponse(response: TestApplicationResponse): MyEntity {
        return JSON.parse(response.content!!)
    }

    override fun createRequest(entity: MyEntity, request: TestApplicationRequest) {
        request.setBody(JSON.stringify(entity))
    }
}
