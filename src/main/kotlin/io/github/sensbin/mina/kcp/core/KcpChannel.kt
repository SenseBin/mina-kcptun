package io.github.sensbin.mina.kcp.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel as KChannel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.net.SocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A KCP channel that wraps a UdpChannel to provide reliable, ordered communication.
 * It handles the KCP state machine, including sending, receiving, and updates.
 */
class KcpChannel(
    conv: Long,
    private val udpChannel: UdpChannel,
    private val kcpOpt: KcpOpt = KcpOpt()
) : Channel {

    private val kcp: KCP
    private val readChannel = KChannel<ByteArray>(KChannel.UNLIMITED)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val updateScheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "kcp-update-thread") }
    private val closed = AtomicBoolean(false)

    init {
        kcp = object : KCP(conv) {
            override fun output(buffer: ByteArray, size: Int) {
                // This is called by KCP when it wants to send a packet.
                // We send it through the underlying UdpChannel.
                coroutineScope.launch {
                    try {
                        udpChannel.write(buffer.copyOf(size))
                    } catch (e: Exception) {
                        // Log error, maybe close channel
                        this@KcpChannel.close()
                    }
                }
            }
        }
        kcp.setMtu(kcpOpt.mtu)
        kcp.wndSize(kcpOpt.sndWnd, kcpOpt.rcvWnd)
        kcp.noDelay(kcpOpt.nodelay, kcpOpt.interval, kcpOpt.resend, kcpOpt.nc)

        start()
    }

    private fun start() {
        // Start a background coroutine to read from the UdpChannel and feed into KCP
        coroutineScope.launch {
            val buffer = ByteArray(kcpOpt.mtu + KCP.IKCP_OVERHEAD)
            while (!isClosed()) {
                try {
                    val n = udpChannel.read(buffer)
                    if (n > 0) {
                        // Received data from UDP, input into KCP state machine
                        kcp.input(buffer.copyOf(n))

                        // After input, check if there is application data to be received
                        while (true) {
                            val peekSize = kcp.peekSize()
                            if (peekSize <= 0) break
                            val recvBuffer = ByteArray(peekSize)
                            val readBytes = kcp.recv(recvBuffer)
                            if (readBytes > 0) {
                                readChannel.send(recvBuffer.copyOf(readBytes))
                            }
                        }
                    } else if (n < 0) {
                        // Underlying channel closed
                        close()
                        break
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        close()
                    }
                    break
                }
            }
        }

        // Schedule periodic KCP updates
        val updateTask = Runnable {
            try {
                if (!isClosed()) {
                    kcp.update(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                // Log exception
            }
        }
        updateScheduler.scheduleAtFixedRate(updateTask, 0, kcpOpt.interval.toLong(), TimeUnit.MILLISECONDS)
    }

    override val remoteAddress: SocketAddress
        get() = udpChannel.remoteAddress

    override val localAddress: SocketAddress
        get() = udpChannel.localAddress

    override suspend fun read(buffer: ByteArray): Int {
        if (isClosed()) return -1
        return try {
            val data = readChannel.receive()
            data.copyInto(buffer)
            data.size
        } catch (e: ClosedReceiveChannelException) {
            -1
        }
    }

    override suspend fun write(buffer: ByteArray): Int {
        if (isClosed()) throw IllegalStateException("Channel is closed")
        kcp.send(buffer)
        return buffer.size
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            coroutineScope.cancel()
            updateScheduler.shutdownNow()
            readChannel.close()
            udpChannel.close()
        }
    }

    override fun isClosed(): Boolean {
        return closed.get() || udpChannel.isClosed()
    }
}
