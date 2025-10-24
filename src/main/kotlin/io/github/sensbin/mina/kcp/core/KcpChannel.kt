package io.github.sensbin.mina.kcp.core

/**
 * A generic channel interface for network communication.
 */
interface KcpChannel {
    /**
     * Reads data from the channel into the given buffer.
     * @param buffer The buffer to read data into.
     * @return The number of bytes read, or -1 if the channel is closed.
     */
    suspend fun read(buffer: ByteArray): Int

    /**
     * Writes data from the buffer to the channel.
     * @param buffer The buffer containing data to write.
     * @return The number of bytes written.
     */
    fun write(buffer: ByteArray): Int

    /**
     * Flushes any buffered data to the channel.
     */
    suspend fun drain(): Unit

    /**
     * Closes the channel.
     */
    fun close()

    /**
     * Checks if the channel is closed.
     * @return true if the channel is closed, false otherwise.
     */
    fun isClosed(): Boolean
}

