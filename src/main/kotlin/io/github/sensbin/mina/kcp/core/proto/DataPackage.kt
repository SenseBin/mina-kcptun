package io.github.sensbin.mina.kcp.core.proto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.xor

/**
 * Data Packet Structure
 *
 * This class represents the structure of a data transmission packet.
 * All multi-byte numerical fields (Length, Long IDs) are assumed to be Big-Endian.
 *
 * Format:
 * | Offset | Size (bytes) | Field Name            | Description                                |
 * |--------|--------------|-----------------------|--------------------------------------------|
 * | 0      | 2            | Total Length (L)      | Total length of the package (including self). Big-Endian (UInt16). |
 * | 2      | 1            | Checksum              | XOR checksum of all bytes *after* this field. |
 * | 3      | 8            | Client ID Low         | The lower 8 bytes (Long) of the client identifier. Big-Endian. |
 * | 11     | 8            | Client ID High        | The upper 8 bytes (Long) of the client identifier. Big-Endian. |
 * | 19     | 8            | Channel ID Low        | The lower 8 bytes (Long) of the channel identifier. Big-Endian. |
 * | 27     | 8            | Channel ID High       | The upper 8 bytes (Long) of the channel identifier. Big-Endian. |
 * | 35     | 1            | Command               | Packet command type (e.g., PING, SYN).     |
 * | 36     | 1            | Compression Mark      | Compression algorithm used for payload.    |
 * | 37     | [L-37]       | Payload (Data)        | Encrypted and/or compressed payload data.  |
 */
class DataPackage(
    val clientIdLow: Long,
    val clientIdHigh: Long,
    val channelIdLow: Long,
    val channelIdHigh: Long,
    val command: Command,
    val compressMark: CompressMark,
    val payload: ByteArray
) {
    companion object {
        const val LONG_SIZE = 8 // Size of a Long in bytes
        const val HEADER_MIN_SIZE = 37 // 2 (Length) + 1 (Checksum) + 4 * 8 (Long IDs) + 1 (Command) + 1 (Compress)

        const val LENGTH_OFFSET = 0
        const val CHECKSUM_OFFSET = 2
        const val DATA_START_OFFSET = 3 // Start of data for checksum (Long IDs, Command, Compress Mark, Payload)

        const val CLIENT_ID_LOW_OFFSET = 3
        const val CLIENT_ID_HIGH_OFFSET = 11
        const val CHANNEL_ID_LOW_OFFSET = 19
        const val CHANNEL_ID_HIGH_OFFSET = 27
        const val COMMAND_OFFSET = 35
        const val COMPRESS_MARK_OFFSET = 36
        const val PAYLOAD_OFFSET = 37

        /**
         * Calculates the XOR checksum for a range of bytes.
         * The checksum is calculated from 'start' (inclusive) to 'end' (exclusive).
         */
        private fun calculateXorChecksum(data: ByteArray, start: Int, end: Int): Byte {
            var checksum: Byte = 0
            for (i in start until end) {
                checksum = checksum xor data[i]
            }
            return checksum
        }

        /**
         * Reads and parses a DataPackage from a byte array starting at a specific offset.
         *
         * @param data The source byte array.
         * @param offset The starting offset in the byte array.
         * @return The parsed DataPackage instance.
         * @throws IllegalArgumentException If the byte array is too short, checksum fails, or a field value is invalid.
         */
        fun readFromByteArray(data: ByteArray, offset: Int = 0): DataPackage {
            if (data.size - offset < HEADER_MIN_SIZE) {
                throw IllegalArgumentException("DataPackage read error: Byte array is too short for minimum header size (${HEADER_MIN_SIZE} bytes).")
            }

            // Use ByteBuffer for correct Big-Endian reads and offset management
            val buffer = ByteBuffer.wrap(data, offset, data.size - offset).order(ByteOrder.BIG_ENDIAN)

            // 1. Read Total Package Length (2 bytes, Big-Endian)
            val totalLength: Int = buffer.short.toInt() and 0xFFFF // Read unsigned short

            if (totalLength < HEADER_MIN_SIZE) {
                throw IllegalArgumentException("DataPackage read error: Total length ($totalLength) is smaller than minimum header size ($HEADER_MIN_SIZE).")
            }
            if (data.size - offset < totalLength) {
                throw IllegalArgumentException("DataPackage read error: Specified total length ($totalLength) exceeds available bytes (${data.size - offset}).")
            }

            // 2. Read XOR Checksum (1 byte)
            val expectedChecksum = buffer.get() // advances buffer to offset 3

            // 3. Calculate actual checksum
            // Checksum covers bytes from DATA_START_OFFSET (3) up to totalLength.
            // We use the original array and offsets because the ByteBuffer has already read 3 bytes.
            val actualChecksum = calculateXorChecksum(data, offset + DATA_START_OFFSET, offset + totalLength)

            if (actualChecksum != expectedChecksum) {
                throw IllegalArgumentException("DataPackage read error: Checksum mismatch. Expected: $expectedChecksum, Actual: $actualChecksum.")
            }

            // 4. Read ID Longs (4 x 8 bytes)
            val clientIdLow = buffer.long
            val clientIdHigh = buffer.long
            val channelIdLow = buffer.long
            val channelIdHigh = buffer.long

            // 5. Read Command and Compress Mark (2 x 1 byte)
            val cmdId = buffer.get()
            val compressMarkId = buffer.get()

            val command = Command.entries.find { it.cmdId == cmdId }
                ?: throw IllegalArgumentException("DataPackage read error: Unsupported Command ID: $cmdId.")

            val compressMark = CompressMark.entries.find { it.compressMarkId == compressMarkId }
                ?: throw IllegalArgumentException("DataPackage read error: Unsupported Compress Mark ID: $compressMarkId.")

            // 6. Extract Payload
            val payloadSize = totalLength - PAYLOAD_OFFSET
            val payload = if (payloadSize > 0) {
                ByteArray(payloadSize).apply {
                    buffer.get(this, 0, payloadSize)
                }
            } else {
                ByteArray(0)
            }

            return DataPackage(clientIdLow, clientIdHigh, channelIdLow, channelIdHigh, command, compressMark, payload)
        }
    }

    /**
     * Writes the DataPackage to a new byte array.
     *
     * @return A new ByteArray representing the entire DataPackage.
     */
    fun writeToByteArray(): ByteArray {
        val totalLength = HEADER_MIN_SIZE + payload.size
        // Check length against unsigned short max value
        require(totalLength <= 65535) { "DataPackage total length exceeds maximum allowed (65535)." }

        val result = ByteArray(totalLength)
        // Use ByteBuffer for Big-Endian writes and simpler offset management
        val buffer = ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN)

        // 1. Total Package Length (2 bytes, Big-Endian)
        buffer.putShort(totalLength.toShort())

        // 2. Placeholder for XOR Checksum (1 byte). Will be updated later.
        val checksumIndex = buffer.position() // Should be 2
        buffer.put(0) // Placeholder

        // 3. ID Longs (4 x 8 bytes)
        buffer.putLong(clientIdLow)
        buffer.putLong(clientIdHigh)
        buffer.putLong(channelIdLow)
        buffer.putLong(channelIdHigh)

        // 4. Command (1 byte)
        buffer.put(command.cmdId)

        // 5. Compress Mark (1 byte)
        buffer.put(compressMark.compressMarkId)

        // 6. Payload (rest bytes)
        if (payload.isNotEmpty()) {
            buffer.put(payload)
        }

        // 7. Calculate and write Checksum
        // Checksum covers bytes from DATA_START_OFFSET (3) up to the end (totalLength)
        val checksum = calculateXorChecksum(result, DATA_START_OFFSET, totalLength)
        result[checksumIndex] = checksum // Write checksum back into the correct position

        return result
    }
}