package io.github.sensbin.mina.kcp

import java.net.SocketAddress

/**
 * A bootstrap class for creating KCP clients.
 * This simplifies the process of setting up a KCP connection.
 */
class KcpClient {
    /**
     * Wraps an existing, connected Channel into a KcpChannel.
     *
     * @param channel The underlying, already connected channel (e.g., a UdpChannel).
     * @param conv The conversation ID. Must be unique for the client-server pair.
     * @param kcpOpt KCP options for tuning the connection.
     * @return A configured KcpChannel ready for communication.
     */
    fun wrap(channel: Channel, conv: Long, kcpOpt: KcpOpt = KcpOpt()): KcpChannel {
        return KcpChannel(conv, channel as UdpChannel, kcpOpt)
    }
}
