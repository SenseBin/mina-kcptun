package io.github.sensbin.mina.kcp.core

import org.apache.mina.filter.logging.LoggingFilter
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
import java.net.SocketAddress

/**
 * A bootstrap class for creating KCP servers.
 * This simplifies the process of binding to a port and accepting KCP connections.
 */
class KcpServer {

    fun bind(
        localAddress: SocketAddress,
        targetAddress: SocketAddress,
        kcpOpt: KcpOpt = KcpOpt(),
    ): KcpServerChannel {
        val acceptor = NioDatagramAcceptor()
        acceptor.sessionConfig.also {
            it.isBroadcast = false
            it.isReuseAddress = false
            it.isCloseOnPortUnreachable = false
            it.receiveBufferSize = 4096
            it.sendBufferSize = 4096
        }
        val kcpServerChannel = KcpServerChannelImpl(localAddress, targetAddress, acceptor, kcpOpt)

        acceptor.handler = kcpServerChannel.getSessionHandler()
        acceptor.bind(localAddress)

        return kcpServerChannel
    }
}