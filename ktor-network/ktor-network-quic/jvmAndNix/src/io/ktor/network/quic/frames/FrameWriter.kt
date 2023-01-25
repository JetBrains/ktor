/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.errors.*
import io.ktor.utils.io.core.*


/**
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-frame-types-and-formats)
 */
internal interface FrameWriter {
    fun writePadding(
        packetBuilder: BytePacketBuilder,
    )

    fun writePing(
        packetBuilder: BytePacketBuilder,
    )

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is decomposed into **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    fun writeACK(
        packetBuilder: BytePacketBuilder,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: LongArray,
    )

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is decomposed into **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    fun writeACKWithECN(
        packetBuilder: BytePacketBuilder,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: LongArray,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    )

    fun writeResetStream(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
        finaSize: Long,
    )

    fun writeStopSending(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
    )

    fun writeCrypto(
        packetBuilder: BytePacketBuilder,
        offset: Long,
        data: ByteArray,
    )

    fun writeNewToken(
        packetBuilder: BytePacketBuilder,
        token: ByteArray,
    )

    fun writeStream(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        offset: Long?,
        length: Long?,
        fin: Boolean,
        data: ByteArray,
    )

    fun writeMaxData(
        packetBuilder: BytePacketBuilder,
        maximumData: Long,
    )

    fun writeMaxStreamData(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        maximumStreamData: Long,
    )

    fun writeMaxStreamsBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    )

    fun writeMaxStreamsUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    )

    fun writeDataBlocked(
        packetBuilder: BytePacketBuilder,
        maximumData: Long,
    )

    fun writeStreamDataBlocked(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        maximumStreamData: Long,
    )

    fun writeStreamsBlockedBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    )

    fun writeStreamsBlockedUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    )

    fun writeNewConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionId: ByteArray,
        statelessResetToken: ByteArray,
    )

    fun writeRetireConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: Long,
    )

    fun writePathChallenge(
        packetBuilder: BytePacketBuilder,
        data: ByteArray,
    )

    fun writePathResponse(
        packetBuilder: BytePacketBuilder,
        data: ByteArray,
    )

    fun writeConnectionCloseWithTransportError(
        packetBuilder: BytePacketBuilder,
        errorCode: QUICTransportError_v1,
        frameTypeV1: FrameType_v1,
        reasonPhrase: ByteArray,
    )

    fun writeConnectionCloseWithAppError(
        packetBuilder: BytePacketBuilder,
        errorCode: AppError_v1,
        reasonPhrase: ByteArray,
    )

    fun writeHandshakeDone(
        packetBuilder: BytePacketBuilder,
    )
}

internal object FrameWriterImpl : FrameWriter {
    override fun writePadding(
        packetBuilder: BytePacketBuilder,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.PADDING)
    }

    override fun writePing(
        packetBuilder: BytePacketBuilder,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.PING)
    }

    override fun writeACK(
        packetBuilder: BytePacketBuilder,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: LongArray,
    ) = packetBuilder.writeACK(
        typeV1 = FrameType_v1.ACK,
        ackDelay = ackDelay,
        ack_delay_exponent = ack_delay_exponent,
        ackRanges = ackRanges,
        ect0 = null,
        ect1 = null,
        ectCE = null
    )

    override fun writeACKWithECN(
        packetBuilder: BytePacketBuilder,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: LongArray,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ) = packetBuilder.writeACK(
        typeV1 = FrameType_v1.ACK_ECN,
        ackDelay = ackDelay,
        ack_delay_exponent = ack_delay_exponent,
        ackRanges = ackRanges,
        ect0 = ect0,
        ect1 = ect1,
        ectCE = ectCE
    )

    private fun BytePacketBuilder.writeACK(
        typeV1: FrameType_v1,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: LongArray,
        ect0: Long?,
        ect1: Long?,
        ectCE: Long?,
    ) {
        require(ackRanges.isNotEmpty()) {
            "'ackRanges' parameter can not be empty"
        }
        require(ackRanges.size % 2 == 0) {
            "'ackRanges' parameter's size should be even"
        }

        val largestAcknowledged = ackRanges.first()
        val firstACKRange = largestAcknowledged - ackRanges[1]
        val ackRangeCount = ackRanges.size / 2 - 1

        writeFrameType(typeV1)
        writeVarInt(largestAcknowledged)
        writeVarInt(ackDelay ushr ack_delay_exponent)
        writeVarInt(ackRangeCount)
        writeVarInt(firstACKRange)

        var lastSmallestAcknowledged = ackRanges[1]
        var i = 2
        while (i < ackRanges.size) {
            val gap = ackRanges[i] - lastSmallestAcknowledged - 2
            lastSmallestAcknowledged = ackRanges[i + 1]
            val length = ackRanges[i] - lastSmallestAcknowledged

            writeVarInt(gap)
            writeVarInt(length)

            i += 2
        }

        ect0?.let {
            writeVarInt(ect0)
            writeVarInt(ect1!!)
            writeVarInt(ectCE!!)
        }
    }

    override fun writeResetStream(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
        finaSize: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.RESET_STREAM)
        writeVarInt(streamId)
        writeErrorCode(applicationProtocolErrorCode)
        writeVarInt(finaSize)
    }

    override fun writeStopSending(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.STOP_SENDING)
        writeVarInt(streamId)
        writeErrorCode(applicationProtocolErrorCode)
    }

    override fun writeCrypto(
        packetBuilder: BytePacketBuilder,
        offset: Long,
        data: ByteArray,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.CRYPTO)
        writeVarInt(offset)
        writeVarInt(data.size) // todo check that should size in bytes
        writeFully(data)
    }

    override fun writeNewToken(
        packetBuilder: BytePacketBuilder,
        token: ByteArray,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.NEW_TOKEN)
        writeVarInt(token.size)
        writeFully(token)
    }

    override fun writeStream(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        offset: Long?,
        length: Long?,
        fin: Boolean,
        data: ByteArray,
    ) = with(packetBuilder) {
        val type = when {
            offset == null && length == null && !fin -> FrameType_v1.STREAM
            offset == null && length == null && fin -> FrameType_v1.STREAM_FIN
            offset == null && length != null && !fin -> FrameType_v1.STREAM_LEN
            offset == null && length != null && fin -> FrameType_v1.STREAM_LEN_FIN
            offset != null && length == null && !fin -> FrameType_v1.STREAM_OFF
            offset != null && length == null && fin -> FrameType_v1.STREAM_OFF_FIN
            offset != null && length != null && !fin -> FrameType_v1.STREAM_OFF_LEN
            offset != null && length != null && fin -> FrameType_v1.STREAM_OFF_LEN_FIN
            else -> error("unreachable")
        }

        writeFrameType(type)
        writeVarInt(streamId)
        offset?.let { writeVarInt(it) }
        length?.let { writeVarInt(it) }
        writeFully(data)
    }

    override fun writeMaxData(
        packetBuilder: BytePacketBuilder,
        maximumData: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.MAX_DATA)
        writeVarInt(maximumData)
    }

    override fun writeMaxStreamData(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        maximumStreamData: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.MAX_STREAM_DATA)
        writeVarInt(streamId)
        writeVarInt(maximumStreamData)
    }

    override fun writeMaxStreamsBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.MAX_STREAMS_BIDIRECTIONAL)
        writeVarInt(maximumStreams)
    }

    override fun writeMaxStreamsUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL)
        writeVarInt(maximumStreams)
    }

    override fun writeDataBlocked(
        packetBuilder: BytePacketBuilder,
        maximumData: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.DATA_BLOCKED)
        writeVarInt(maximumData)
    }

    override fun writeStreamDataBlocked(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        maximumStreamData: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.STREAM_DATA_BLOCKED)
        writeVarInt(streamId)
        writeVarInt(maximumStreamData)
    }

    override fun writeStreamsBlockedBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL)
        writeVarInt(maximumStreams)
    }

    override fun writeStreamsBlockedUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL)
        writeVarInt(maximumStreams)
    }

    override fun writeNewConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionId: ByteArray,
        statelessResetToken: ByteArray,
    ) = with(packetBuilder) {
        require(retirePriorTo <= sequenceNumber) {
            "The value in the 'Retire Prior To' field in NEW_CONNECTION_ID frame MUST be less than or equal to the value in the 'Sequence Number' field"
        }
        require(connectionId.size in 1..20) {
            "The size of the value in the 'Connection ID' field in NEW_CONNECTION_ID frame MUST be at least 1 byte and at most 20 bytes"
        }
        require(statelessResetToken.size == 16) {
            "The size of the value in the 'Stateless Reset Token' field in NEW_CONNECTION_ID frame MUST be 16 bytes"
        }

        writeFrameType(FrameType_v1.NEW_CONNECTION_ID)
        writeVarInt(sequenceNumber)
        writeVarInt(retirePriorTo)
        writeConnectionId(connectionId)
        writeFully(statelessResetToken)
    }

    override fun writeRetireConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: Long,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.RETIRE_CONNECTION_ID)
        writeVarInt(sequenceNumber)
    }

    override fun writePathChallenge(
        packetBuilder: BytePacketBuilder,
        data: ByteArray,
    ) = with(packetBuilder) {
        require(data.size == 8) {
            "The size of the value in the 'Data' field in PATH_CHALLENGE frame MUST be 8 bytes"
        }
        writeFrameType(FrameType_v1.PATH_CHALLENGE)
        writeFully(data)
    }

    override fun writePathResponse(
        packetBuilder: BytePacketBuilder,
        data: ByteArray,
    ) = with(packetBuilder) {
        require(data.size == 8) {
            "The size of the value in the 'Data' field in PATH_RESPONSE frame MUST be 8 bytes"
        }
        writeFrameType(FrameType_v1.PATH_RESPONSE)
        writeFully(data)
    }

    override fun writeConnectionCloseWithTransportError(
        packetBuilder: BytePacketBuilder,
        errorCode: QUICTransportError_v1,
        frameTypeV1: FrameType_v1,
        reasonPhrase: ByteArray,
    ) = writeConnectionClose(
        packetBuilder = packetBuilder,
        typeV1 = FrameType_v1.CONNECTION_CLOSE_TRANSPORT_ERR,
        errorCode = errorCode,
        frameTypeV1 = frameTypeV1,
        reasonPhrase = reasonPhrase
    )

    override fun writeConnectionCloseWithAppError(
        packetBuilder: BytePacketBuilder,
        errorCode: AppError_v1,
        reasonPhrase: ByteArray,
    ) = writeConnectionClose(
        packetBuilder = packetBuilder,
        typeV1 = FrameType_v1.CONNECTION_CLOSE_APP_ERR,
        errorCode = errorCode,
        frameTypeV1 = null,
        reasonPhrase = reasonPhrase
    )

    private fun writeConnectionClose(
        packetBuilder: BytePacketBuilder,
        typeV1: FrameType_v1,
        errorCode: Error_v1,
        frameTypeV1: FrameType_v1?,
        reasonPhrase: ByteArray,
    ) = with(packetBuilder) {
        writeFrameType(typeV1)
        writeErrorCode(errorCode)
        frameTypeV1?.let { writeFrameType(it) }
        writeVarInt(reasonPhrase.size)
        writeFully(reasonPhrase)
    }

    override fun writeHandshakeDone(
        packetBuilder: BytePacketBuilder,
    ) = with(packetBuilder) {
        writeFrameType(FrameType_v1.HANDSHAKE_DONE)
    }

    // HELPER FUNCTIONS AND VALUES

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun BytePacketBuilder.writeFrameType(typeV1: FrameType_v1) {
        // it is actually a varint with length 8, as frame types are all values in 0x00..0x1e
        writeUByte(typeV1.typeValue)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun BytePacketBuilder.writeConnectionId(id: ByteArray) {
        writeUByte(id.size.toUByte())
        writeFully(id)
    }

    private fun BytePacketBuilder.writeErrorCode(errorCode: Error_v1) {
        errorCode.writeToFrame(this)
    }
}
