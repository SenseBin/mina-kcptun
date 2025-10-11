package io.github.sensbin.mina.kcp

import io.github.sensbin.mina.kcp.core.KcpChannel
import io.github.sensbin.mina.kcp.core.KcpOpt
import io.github.sensbin.mina.kcp.core.UdpChannel
import java.net.SocketAddress

/**
 * A bootstrap class for creating KCP clients.
 * This simplifies the process of setting up a KCP connection.
 */
class KcpClient {
    /**
     * Connects to a remote address and returns a KcpChannel.
     *
     * @param remoteAddress The remote address to connect to.
     * @param conv The conversation ID. Must be unique for the client-server pair.
     * @param kcpOpt KCP options for tuning the connection.
     * @return A configured KcpChannel ready for communication.
     */
    fun connect(remoteAddress: SocketAddress, conv: Long, kcpOpt: KcpOpt = KcpOpt()): KcpChannel {
        val udpChannel = UdpChannel.connect(remoteAddress)
        return KcpChannel(conv, udpChannel, kcpOpt)
    }
}
