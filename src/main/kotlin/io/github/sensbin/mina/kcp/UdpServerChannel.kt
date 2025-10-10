package io.github.sensbin.mina.kcp

import kotlinx.coroutines.*
import org.apache.mina.core.service.IoHandler
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.codec.ProtocolCodecFilter
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
import java.net.InetSocketAddress

/**
 * 基于 MINA 的 UDP 服务器通道。
 * 支持 accept 新连接，返回 UdpChannel。
 */
class UdpServerChannel(
    private val localAddress: InetSocketAddress,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Channel {
    private var acceptor: NioDatagramAcceptor? = null
    private val acceptChannel = coroutineScope.channel<UdpChannel>()
    override var isClosed = false
        private set

    init {
        startMinaAcceptor()
    }

    private fun startMinaAcceptor() {
        acceptor = NioDatagramAcceptor()
        acceptor!!.filterChain.addLast("codec", ProtocolCodecFilter())
        acceptor!!.handler = object : IoHandler {
            override fun sessionCreated(session: IoSession) {
                // 为每个新会话创建一个 UdpChannel
                val newChannel = UdpChannel(session.remoteAddress as InetSocketAddress, coroutineScope)
                // 注意：这里需要将 session 绑定到 newChannel 的 MINA 逻辑中，简化版用通道模拟
                coroutineScope.launch {
                    acceptChannel.send(newChannel)
                }
            }

            override fun sessionOpened(session: IoSession) {}
            override fun sessionClosed(session: IoSession) {}
            override fun messageReceived(session: IoSession, message: Any) {
                // 服务器端消息转发逻辑可扩展
            }

            override fun messageSent(session: IoSession, message: Any) {}
            override fun exceptionCaught(session: IoSession, cause: Throwable) {
                cause.printStackTrace()
            }

            override fun inputClosed(session: IoSession) {}
        }
        acceptor!!.bind(localAddress)
    }

    /**
     * 接受新连接，返回 UdpChannel。
     */
    suspend fun accept(): UdpChannel {
        if (isClosed) throw IllegalStateException("Server channel is closed")
        return acceptChannel.receive()
    }

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // 服务器通道主要用于 accept，不直接读写
        throw UnsupportedOperationException("Server channel does not support direct read")
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        // 服务器通道主要用于 accept，不直接读写
        throw UnsupportedOperationException("Server channel does not support direct write")
    }

    override suspend fun close() {
        if (!isClosed) {
            isClosed = true
            acceptor?.unbind()
            acceptor?.dispose()
            acceptChannel.close()
            coroutineScope.cancel()
        }
    }
}