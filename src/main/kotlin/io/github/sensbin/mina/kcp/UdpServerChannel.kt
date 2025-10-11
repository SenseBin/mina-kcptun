package io.github.sensbin.mina.kcp

import kotlinx.coroutines.channels.Channel as KChannel
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.service.IoHandlerAdapter
import org.apache.mina.core.session.IoSession
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于 MINA 的 UDP 服务器通道。
 * 监听指定地址，并为每个新的远程地址创建一个 UdpChannel。
 */
class UdpServerChannel(
    override val localAddress: InetSocketAddress
) : ServerChannel {

    private val acceptor: NioDatagramAcceptor = NioDatagramAcceptor()
    private val newChannels = KChannel<Channel>(KChannel.UNLIMITED)
    private val establishedChannels = ConcurrentHashMap<SocketAddress, UdpChannel>()
    private val closed = AtomicBoolean(false)

    init {
        acceptor.handler = ServerIoHandler()
        acceptor.sessionConfig.isReuseAddress = true
        acceptor.bind(localAddress)
    }

    private inner class ServerIoHandler : IoHandlerAdapter() {
        override fun messageReceived(session: IoSession, message: Any) {
            val remoteAddress = session.remoteAddress
            val channel = establishedChannels.computeIfAbsent(remoteAddress) {
                val newChannel = UdpChannel(session, KChannel(KChannel.UNLIMITED))
                newChannels.trySend(newChannel) // 将新 channel 放入 accept 队列
                newChannel
            }

            if (message is IoBuffer) {
                val bytes = ByteArray(message.remaining())
                message.get(bytes)
                // 将数据发送到对应的 UdpChannel
                channel.incomingMessages.trySend(bytes)
            }
        }

        override fun sessionClosed(session: IoSession) {
            establishedChannels.remove(session.remoteAddress)?.close()
        }

        override fun exceptionCaught(session: IoSession, cause: Throwable) {
            // 简单打印异常，实际应用中应使用日志框架
            cause.printStackTrace()
            session.closeNow()
        }
    }

    override suspend fun accept(): Channel {
        if (isClosed()) {
            throw IllegalStateException("Server channel is closed")
        }
        return newChannels.receive()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            acceptor.unbind()
            acceptor.dispose(true)
            newChannels.close()
            establishedChannels.values.forEach { it.close() }
            establishedChannels.clear()
        }
    }

    override fun isClosed(): Boolean {
        return closed.get()
    }
}