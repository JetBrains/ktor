package io.ktor.client.engine.winhttp

import io.ktor.client.engine.*

class WinHttpClientEngineConfig : HttpClientEngineConfig() {
    /**
     * A value of type integer that specifies the time-out value, in milliseconds, to use for name resolution.
     * If resolution takes longer than this time-out value, the action is canceled.
     * The initial value is zero, meaning no time-out (infinite).
     */
    var resolveTimeout: Int = 0

    /**
     * A value of type integer that specifies the time-out value, in milliseconds, to use for server connection requests.
     * If a connection request takes longer than this time-out value, the request is canceled.
     * The initial value is 60,000 (60 seconds).
     */
    var connectTimeout: Int = 60_000

    /**
     * A value of type integer that specifies the time-out value, in milliseconds, to use for sending requests.
     * If sending a request takes longer than this time-out value, the send is canceled.
     * The initial value is 30,000 (30 seconds).
     */
    var sendTimeout: Int = 30_000

    /**
     * A value of type integer that specifies the time-out value, in milliseconds, to receive a response to a request.
     * If a response takes longer than this time-out value, the request is canceled.
     * The initial value is 30,000 (30 seconds).
     */
    var receiveTimeout: Int = 30_000
}
