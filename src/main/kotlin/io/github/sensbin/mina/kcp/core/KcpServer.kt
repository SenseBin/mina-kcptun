package io.github.sensbin.mina.kcp.core

import org.apache.mina.filter.logging.LoggingFilter
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
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
    fun bind(localAddress: SocketAddress, kcpOpt: KcpOpt = KcpOpt()): KcpServerChannelImpl {
        val acceptor = NioDatagramAcceptor()
        acceptor.sessionConfig.also {
            it.isBroadcast = false
            it.isReuseAddress = false
            it.isCloseOnPortUnreachable = true
            it.receiveBufferSize = 4096
            it.sendBufferSize = 4096
        }
        val kcpServerChannel = KcpServerChannelImpl(localAddress, acceptor, kcpOpt)
        acceptor.filterChain.addLast("logging", LoggingFilter())
        acceptor.handler = kcpServerChannel.getSessionHandler()
        acceptor.bind(localAddress)

        return kcpServerChannel
    }
}