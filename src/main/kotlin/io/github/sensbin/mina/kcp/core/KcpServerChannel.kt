package io.github.sensbin.mina.kcp.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel as KChannel
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.service.IoHandlerAdapter
import org.apache.mina.core.session.IoSession
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A server-side channel that listens for incoming KCP connections.
 * It manages multiple KcpChannel instances, one for each client.
 */
class KcpServerChannel(
    override val localAddress: SocketAddress,
    private val kcpOpt: KcpOpt = KcpOpt()
) : ServerChannel {
    private val acceptor = NioDatagramAcceptor()
    private val newChannels = KChannel<KcpChannel>(KChannel.UNLIMITED)
    private val managedChannels = ConcurrentHashMap<SocketAddress, KcpChannel>()
    private val sessionUdpChannels = ConcurrentHashMap<SocketAddress, UdpChannel>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val closed = AtomicBoolean(false)

    init {
        acceptor.handler = object : IoHandlerAdapter() {
            override fun messageReceived(session: IoSession, message: Any) {
                val remoteAddress = session.remoteAddress
                val buffer = message as IoBuffer
                // It's crucial that the conv ID is part of every packet to route it correctly.
                val conv = KCP.ikcp_decode32u(buffer.buf().array(), buffer.position())

                // Get or create the UdpChannel for this session
                val udpChannel = sessionUdpChannels.computeIfAbsent(remoteAddress) {
                    val incoming = KChannel<ByteArray>(KChannel.UNLIMITED)
                    UdpChannel(session, incoming)
                }

                // Get or create the KcpChannel for this conversation
                managedChannels.computeIfAbsent(remoteAddress) {
                    val newKcpChannel = KcpChannel(conv, udpChannel, kcpOpt)
                    // Offer the newly created channel to the acceptor
                    newChannels.trySend(newKcpChannel)
                    newKcpChannel
                }

                // Pass the raw message to the underlying UdpChannel's incoming queue
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                (udpChannel.incomingMessages as KChannel).trySend(bytes)
            }

            override fun exceptionCaught(session: IoSession, cause: Throwable) {
                cause.printStackTrace()
                session.closeNow()
            }

            override fun sessionClosed(session: IoSession) {
                val remoteAddress = session.remoteAddress
                managedChannels.remove(remoteAddress)?.close()
                sessionUdpChannels.remove(remoteAddress)
            }
        }
        acceptor.bind(localAddress)
    }

    /**
     * Accepts a new incoming KcpChannel.
     * This is a suspending function that waits for a new client to connect.
     * @return A new KcpChannel for communication with the client.
     */
    override suspend fun accept(): KcpChannel {
        return newChannels.receive()
    }

    /**
     * Closes the server channel and all managed KCP channels.
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            acceptor.unbind()
            acceptor.dispose(true)
            managedChannels.values.forEach { it.close() }
            newChannels.close()
            scope.cancel()
        }
    }

    override fun isClosed(): Boolean {
        return closed.get()
    }
}
