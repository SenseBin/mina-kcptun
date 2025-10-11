package io.github.sensbin.mina.kcp.core

import kotlinx.coroutines.channels.Channel as KChannel
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.service.IoHandlerAdapter
import org.apache.mina.core.session.IoSession
import org.apache.mina.transport.socket.nio.NioDatagramConnector
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于 MINA 的 UDP 通道实现。
 *
 * @param session MINA 的 IoSession
 * @param incomingMessages 用于接收消息的 Kotlin Channel
 */
class UdpChannel(
    internal val session: IoSession,
    internal val incomingMessages: KChannel<ByteArray>
) : Channel {

    private val closed = AtomicBoolean(false)

    override val remoteAddress: SocketAddress
        get() = session.remoteAddress

    override val localAddress: SocketAddress
        get() = session.localAddress

    override suspend fun read(buffer: ByteArray): Int {
        if (isClosed()) {
            return -1
        }
        val data = incomingMessages.receiveCatching().getOrNull() ?: return -1
        data.copyInto(buffer)
        return data.size
    }

    override suspend fun write(buffer: ByteArray): Int {
        if (isClosed()) {
            throw IllegalStateException("Channel is closed")
        }
        val ioBuffer = IoBuffer.wrap(buffer)
        session.write(ioBuffer)
        return buffer.size
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            // UdpServerChannel 会管理 session 的关闭
            // 如果是客户端主动关闭，则需要关闭 session
            if (session.service is NioDatagramConnector) {
                session.closeNow()
            }
            incomingMessages.close()
        }
    }

    override fun isClosed(): Boolean {
        return closed.get() || !session.isActive
    }

    companion object {
        /**
         * 连接到远程 UDP 地址并创建一个 UdpChannel。
         * @param remoteAddress 远程服务器地址
         * @return UdpChannel 实例
         */
        fun connect(remoteAddress: SocketAddress): UdpChannel {
            val connector = NioDatagramConnector()
            val incomingMessages = KChannel<ByteArray>(KChannel.UNLIMITED)

            connector.handler = object : IoHandlerAdapter() {
                override fun messageReceived(session: IoSession, message: Any) {
                    if (message is IoBuffer) {
                        val bytes = ByteArray(message.remaining())
                        message.get(bytes)
                        incomingMessages.trySend(bytes)
                    }
                }

                override fun exceptionCaught(session: IoSession, cause: Throwable) {
                    cause.printStackTrace()
                    incomingMessages.close(cause)
                    session.closeNow()
                }

                override fun sessionClosed(session: IoSession) {
                    incomingMessages.close()
                }
            }

            val connectFuture = connector.connect(remoteAddress)
            connectFuture.awaitUninterruptibly()
            return UdpChannel(connectFuture.session, incomingMessages)
        }
    }
}