package io.github.sensbin.mina.kcp.core

import java.net.SocketAddress

/**
 * A generic channel interface for network communication.
 */
interface Channel {
    /**
     * The remote address of the channel.
     */
    val remoteAddress: SocketAddress

    /**
     * The local address of the channel.
     */
    val localAddress: SocketAddress

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
    suspend fun write(buffer: ByteArray): Int

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

