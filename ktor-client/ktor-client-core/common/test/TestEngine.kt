import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

internal object TestEngine : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher
        get() = Dispatchers.Default

    override val config: HttpClientEngineConfig = HttpClientEngineConfig()

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        TODO("Not yet implemented")
    }

    override val coroutineContext: CoroutineContext
        get() = Job()

    override fun close() {
    }
}
