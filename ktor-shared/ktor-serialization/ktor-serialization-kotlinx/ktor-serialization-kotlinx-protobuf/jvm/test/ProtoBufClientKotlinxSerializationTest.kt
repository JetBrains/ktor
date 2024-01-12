import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class ProtoBufClientKotlinxSerializationTest : AbstractClientContentNegotiationTest() {
    private val converter = KotlinxSerializationConverter(DefaultProtoBuf)
    override val defaultContentType: ContentType = ContentType.Application.ProtoBuf
    override val customContentType: ContentType = ContentType.parse("text/protobuf")
    override val webSocketsConverter: WebsocketContentConverter =
        KotlinxWebsocketSerializationConverter(DefaultProtoBuf)

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, converter)
    }

    override suspend fun <T : Any> ApplicationCall.respond(
        responseJson: String,
        contentType: ContentType,
        serializer: KSerializer<T>
    ) {
        val actual = Json.decodeFromString(serializer, responseJson)
        val bytes = DefaultProtoBuf.encodeToByteArray(serializer, actual)
        respondBytes(bytes, contentType)
    }

    override suspend fun ApplicationCall.respondWithRequestBody(contentType: ContentType) {
        respondBytes(receive(), contentType)
    }

    @Test
    @Ignore
    override fun testSerializeNull() {
    }

    @Test
    @Ignore
    override fun testSerializeFailureHasOriginalCauseMessage() {
    }
}
