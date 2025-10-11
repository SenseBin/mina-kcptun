package io.github.sensbin.mina.kcp

import io.github.sensbin.mina.kcp.core.KcpOpt
import io.github.sensbin.mina.kcp.core.KcpServerChannel
import java.net.SocketAddress

/**
 * A bootstrap class for creating KCP servers.
 * This simplifies the process of binding to a port and accepting KCP connections.
 */
class KcpServer {
    /**
     * Binds to a local address and returns a KcpServerChannel.
     * The returned channel can be used to accept incoming KCP connections.
     *
     * @param localAddress The local address to bind to.
     * @param kcpOpt KCP options that will be applied to all accepted channels.
     * @return A configured KcpServerChannel.
     */
    fun bind(localAddress: SocketAddress, kcpOpt: KcpOpt = KcpOpt()): KcpServerChannel {
        return KcpServerChannel(localAddress, kcpOpt)
    }

    /**
     * Wraps an existing ServerChannel to produce KcpChannels.
     *
     * @param serverChannel The underlying server channel (e.g., a UdpServerChannel).
     * @param kcpOpt KCP options that will be applied to all accepted channels.
     * @return A configured KcpServerChannel.
     */
    fun wrap(serverChannel: ServerChannel, kcpOpt: KcpOpt = KcpOpt()): KcpServerChannel {
        return KcpServerChannel(serverChannel, kcpOpt)
    }
}
