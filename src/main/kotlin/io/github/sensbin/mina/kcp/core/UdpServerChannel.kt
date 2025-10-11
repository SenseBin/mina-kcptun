package io.github.sensbin.mina.kcp.core

import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.service.IoHandlerAdapter
import org.apache.mina.core.session.IoSession
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel as KChannel

/**
 * A server-side channel that listens for incoming UDP packets and creates UdpChannel instances for each new client.
 */
class UdpServerChannel(
    localAddress: SocketAddress
) {
    private val acceptor = NioDatagramAcceptor()
    private val sessionChannels = ConcurrentHashMap<SocketAddress, UdpChannel>()
    private val newChannels = KChannel<UdpChannel>(KChannel.UNLIMITED)

    init {
        acceptor.handler = object : IoHandlerAdapter() {
            override fun messageReceived(session: IoSession, message: Any) {
                val remoteAddress = session.remoteAddress
                val channel = sessionChannels.computeIfAbsent(remoteAddress) {
                    val incoming = KChannel<ByteArray>(KChannel.UNLIMITED)
                    UdpChannel(session, incoming)
                }

                if (message is IoBuffer) {
                    val bytes = ByteArray(message.remaining())
                    message.get(bytes)
                    channel.incomingMessages.trySend(bytes)
                }

                // Notify that a new channel is effectively created and has received its first message
                if (channel.session.getAttribute("new") == null) {
                    channel.session.setAttribute("new")
                    newChannels.trySend(channel)
                }
            }

            override fun exceptionCaught(session: IoSession, cause: Throwable) {
                cause.printStackTrace()
                sessionChannels.remove(session.remoteAddress)?.close()
                session.closeNow()
            }

            override fun sessionClosed(session: IoSession) {
                sessionChannels.remove(session.remoteAddress)?.close()
            }
        }
        acceptor.bind(localAddress)
    }

    /**
     * Accepts a new incoming UdpChannel.
     * @return A new UdpChannel.
     */
    suspend fun accept(): UdpChannel {
        return newChannels.receive()
    }

    /**
     * Closes the server channel and all associated client channels.
     */
    fun close() {
        acceptor.unbind()
        acceptor.dispose()
        sessionChannels.values.forEach { it.close() }
        newChannels.close()
    }
}

