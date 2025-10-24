package io.github.sensbin.mina.kcp.core

import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
import org.apache.mina.transport.socket.nio.NioSocketAcceptor
import org.apache.mina.transport.socket.nio.NioSocketConnector
import java.net.SocketAddress

/**
 * A bootstrap class for creating KCP clients.
 * This simplifies the process of setting up a KCP connection.
 */
class KcpClient {

    suspend fun connect(
        localAddress: SocketAddress,
        remoteAddress: SocketAddress,
        kcpOpt: KcpOpt = KcpOpt()
    ): KcpChannel {
        val acceptor = NioSocketAcceptor()
        acceptor.sessionConfig.also {
            it.isReuseAddress = false
            it.soLinger = 0
            it.isTcpNoDelay = true
        }
        val kcpChannel = KcpChannelImpl(localAddress, remoteAddress, acceptor, kcpOpt)

        acceptor.handler = kcpChannel.getSessionHandler()
        acceptor.bind(localAddress)

        return kcpChannel
    }
}