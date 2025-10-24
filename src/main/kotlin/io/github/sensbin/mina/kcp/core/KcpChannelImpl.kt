package io.github.sensbin.mina.kcp.core

import java.net.SocketAddress

/**
 * A KCP channel that wraps a UdpChannel to provide reliable, ordered communication.
 * It handles the KCP state machine, including sending, receiving, and updates.
 */
class KcpChannelImpl internal constructor(
    private val remoteSocketAddress: SocketAddress
) : KcpChannel {
    override suspend fun read(buffer: ByteArray): Int {
        TODO("Not yet implemented")
    }

    override fun write(buffer: ByteArray): Int {
        TODO("Not yet implemented")
    }

    override suspend fun drain() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

}
