/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.errors.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*

internal inline fun transportParameters(config: TransportParameters.() -> Unit = {}): TransportParameters {
    return TransportParameters().apply(config)
}

/**
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-transport-parameter-definit)
 */
@Suppress("PropertyName")
internal class TransportParameters {
    /**
     * This parameter is the value of the Destination Connection ID field from the first Initial packet
     * sent by the client; see [Section 7.3](https://www.rfc-editor.org/rfc/rfc9000.html#cid-auth).
     * This transport parameter is only sent by a server.
     */
    val original_destination_connection_id: ByteArray? = null

    /**
     * The maximum idle timeout is a value in milliseconds that is encoded as an integer;
     * see [Section 10.1](https://www.rfc-editor.org/rfc/rfc9000.html#idle-timeout).
     * Idle timeout is disabled when both endpoints omit this transport parameter or specify a value of 0.
     */
    var max_idle_timeout: Long = 0

    /**
     * A stateless reset token is used in verifying a stateless reset;
     * see Section 10.3.
     * This parameter is a sequence of 16 bytes.
     * This transport parameter MUST NOT be sent by a client but MAY be sent by a server.
     * A server that does not send this transport parameter cannot use stateless reset for the connection ID
     * negotiated during the handshake.
     */
    var stateless_reset_token: ByteArray? = null

    /**
     * The maximum UDP payload size parameter is an integer value that limits the size of UDP payloads
     * that the endpoint is willing to receive.
     * UDP datagrams with payloads larger than this limit are not likely to be processed by the receiver.
     *
     * The default for this parameter is the maximum permitted UDP payload of 65527.
     * Values below 1200 are invalid.
     */
    var max_udp_payload_size: Long = 65527

    /**
     * The initial maximum data parameter is an integer value
     * that contains the initial value for the maximum amount of data that can be sent on the connection.
     * This is equivalent to sending a MAX_DATA
     * for the connection immediately after completing the handshake.
     */
    var initial_max_data: Long = 0

    /**
     * This parameter is an integer value
     * specifying the initial flow control limit for locally initiated bidirectional streams.
     * This limit applies to newly created bidirectional streams
     * opened by the endpoint that sends the transport parameter.
     * In client transport parameters
     * this applies to streams with an identifier with the least significant two bits set to 0x00;
     * in server transport parameters this applies to streams with the least significant two bits set to 0x01.
     */
    var initial_max_stream_data_bidi_local: Long = 0

    /**
     * This parameter is an integer value
     * specifying the initial flow control limit for peer-initiated bidirectional streams.
     * This limit applies to newly created bidirectional streams
     * opened by the endpoint that receives the transport parameter.
     * In client transport parameters
     * this applies to streams with an identifier with the least significant two bits set to 0x01;
     * in server transport parameters this applies to streams with the least significant two bits set to 0x00.
     */
    var initial_max_stream_data_bidi_remote: Long = 0

    /**
     * This parameter is an integer value specifying the initial flow control limit for unidirectional streams.
     * This limit applies to newly created unidirectional streams
     * opened by the endpoint that receives the transport parameter.
     * In client transport parameters
     * this applies to streams with an identifier with the least significant two bits set to 0x03;
     * in server transport parameters this applies to streams with the least significant two bits set to 0x02.
     */
    var initial_max_stream_data_uni: Long = 0

    /**
     * The initial maximum bidirectional streams parameter is an integer value
     * that contains the initial maximum number of bidirectional streams
     * the endpoint that receives this transport parameter is permitted to initiate.
     * If this parameter is absent or zero
     * the peer cannot open bidirectional streams until a MAX_STREAMS frame is sent.
     * Setting this parameter is equivalent to sending a MAX_STREAMS
     * of the corresponding type with the same value.
     */
    var initial_max_streams_bidi: Long = 0

    /**
     * The initial maximum unidirectional streams parameter is an integer value
     * that contains the initial maximum number of unidirectional streams
     * the endpoint that receives this transport parameter is permitted to initiate.
     * If this parameter is absent or zero
     * the peer cannot open unidirectional streams until a MAX_STREAMS frame is sent.
     * Setting this parameter is equivalent to sending a MAX_STREAMS
     * of the corresponding type with the same value.
     */
    var initial_max_streams_uni: Long = 0

    /**
     * The acknowledgment delay exponent is an integer value
     * indicating an exponent used to decode the ACK Delay field in the ACK frame.
     * If this value is absent a default value of 3 is assumed (indicating a multiplier of 8).
     * The values above 20 are invalid.
     */
    var ack_delay_exponent: Int = 3

    /**
     * The maximum acknowledgment delay is an integer value
     * indicating the maximum amount of time in milliseconds
     * by which the endpoint will delay sending acknowledgments.
     * This value SHOULD include the receiver's expected delays in alarms firing.
     * For example if a receiver sets a timer for 5ms and alarms commonly fire up to 1ms late
     * then it should send a max_ack_delay of 6ms.
     * If this value is absent a default of 25 milliseconds is assumed.
     * Values of 214 or greater are invalid.
     */
    var max_ack_delay: Long = 25

    /**
     * The disable active migration transport parameter is included
     * if the endpoint does not support active connection migration
     * (Section 9) on the address being used during the handshake.
     * An endpoint that receives this transport parameter MUST NOT use a new local address
     * when sending to the address that the peer used during the handshake.
     * This transport parameter does not prohibit connection migration
     * after a client has acted on a preferred_address transport parameter.
     * This parameter is a zero-length value.
     */
    var disable_active_migration: Long = 0

    /**
     * The server's preferred address is used to effect a change in server address at the end of the handshake
     * as described in Section 9.6.
     * This transport parameter is only sent by a server.
     * Servers MAY choose to only send a preferred address of one address family by sending an all-zero address and port
     * (0.0.0.0:0 or [::]:0) for the other family.
     * IP addresses are encoded in network byte order.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#section-18.2-4.32.1)
     */
    var preferred_address: PreferredAddress? = null

    /**
     * This is an integer value
     * specifying the maximum number of connection IDs from the peer that an endpoint is willing to store.
     * This value includes the connection ID received during the handshake
     * that received in the preferred_address transport parameter
     * and those received in NEW_CONNECTION_ID frames.
     * The value of the active_connection_id_limit parameter MUST be at least 2. An endpoint
     * that receives a value less than 2 MUST close the connection with an error of type TRANSPORT_PARAMETER_ERROR.
     * If this transport parameter is absent a default of 2 is assumed.
     * If an endpoint issues a zero-length connection ID
     * it will never send a NEW_CONNECTION_ID frame
     * and therefore ignores the active_connection_id_limit value received from its peer.
     */
    var active_connection_id_limit: Int = 2

    /**
     * This is the value that the endpoint included in the Source Connection ID field of the first Initial packet
     * it sends for the connection;
     * see Section 7.3.
     */
    var initial_source_connection_id: ByteArray? = null

    /**
     * This is the value that the server included in the Source Connection ID field of a Retry packet; see Section 7.3.
     * This transport parameter is only sent by a server.
     */
    var retry_source_connection_id: ByteArray? = null
}

/**
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-preferred-address-format)
 */
internal class PreferredAddress(
    val ipv4address: Int,
    val ipv4Port: Short,
    val ipv6address: Int,
    val ipv6Port: Short,
    val connectionID: ConnectionID,
    val statelessResetToken: ByteArray,
)