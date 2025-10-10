package io.github.sensbin.mina.kcp

import kotlinx.coroutines.*
import org.apache.mina.core.future.ConnectFuture
import org.apache.mina.core.service.IoHandler
import org.apache.mina.core.session.IdleStatus
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.FilterEvent
import org.apache.mina.transport.socket.nio.NioDatagramConnector
import java.net.InetSocketAddress

/**
 * 基于 MINA 的 UDP 通道实现（客户端）。
 */
class UdpChannel(
    private val remoteAddress: InetSocketAddress,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Channel {
    private var session: IoSession? = null
    private val readChannel = coroutineScope.channel<ByteArray>()
    private val writeChannel = coroutineScope.channel<ByteArray>()
    private var closed = false

    init {
        startMinaConnector()
    }

    private fun startMinaConnector() {
        val connector = NioDatagramConnector()
        connector.handler = object : IoHandler {
            override fun sessionCreated(session: IoSession) {
                this@UdpChannel.session = session
                // 启动读写协程
                coroutineScope.launch {
                    handleReading()
                }
                coroutineScope.launch {
                    handleWriting()
                }
            }

            override fun sessionOpened(session: IoSession) {}
            override fun sessionClosed(session: IoSession) {
                close()
            }

            override fun sessionIdle(session: IoSession?, status: IdleStatus?) {
            }

            override fun messageReceived(session: IoSession, message: Any) {
                if (message is ByteArray) {
                    readChannel.trySend(message)
                }
            }

            override fun messageSent(session: IoSession, message: Any) {}
            override fun exceptionCaught(session: IoSession, cause: Throwable) {
                cause.printStackTrace()
                close()
            }

            override fun inputClosed(session: IoSession) {}
            override fun event(session: IoSession?, event: FilterEvent?) {
            }
        }

        val future: ConnectFuture = connector.connect(remoteAddress)
        future.await() // 阻塞等待连接
        if (!future.isConnected) {
            throw RuntimeException("Failed to connect to $remoteAddress")
        }
    }

    private suspend fun handleReading() {
        try {
            while (!closed) {
                val data = readChannel.receive()
                // 这里可以扩展为实际的 ByteArray 处理
            }
        } catch (e: Exception) {
            if (!closed) e.printStackTrace()
        }
    }

    private suspend fun handleWriting() {
        try {
            while (!closed) {
                val data = writeChannel.receive()
                session?.write(data)
            }
        } catch (e: Exception) {
            if (!closed) e.printStackTrace()
        }
    }

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) throw IllegalStateException("Channel is closed")
        // 模拟读取：实际中从 MINA session 读取，这里用协程通道简化
        val data = readChannel.receiveOrNull() ?: ByteArray(0)
        val actualLength = minOf(data.size, length)
        System.arraycopy(data, 0, buffer, offset, actualLength)
        return actualLength
    }

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) throw IllegalStateException("Channel is closed")
        val data = buffer.copyOfRange(offset, offset + length)
        writeChannel.send(data)
        return length
    }

    override suspend fun close() {
        if (!closed) {
            closed = true
            session?.closeNow()
            readChannel.close()
            writeChannel.close()
            coroutineScope.cancel()
        }
    }

    override fun isClosed() = closed
}