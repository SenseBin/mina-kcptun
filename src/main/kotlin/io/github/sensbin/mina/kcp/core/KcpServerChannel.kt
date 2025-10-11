package io.github.sensbin.mina.kcp.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel as KChannel

/**
 * A server-side channel that wraps a lower-level ServerChannel to produce KcpChannels.
 * It accepts raw Channels from the underlying server and wraps them in KcpChannel.
 */
class KcpServerChannel(
    private val underlyingServerChannel: ServerChannel,
    private val kcpOpt: KcpOpt = KcpOpt()
) : ServerChannel {
    private val newKcpChannels = KChannel<KcpChannel>(KChannel.UNLIMITED)
    private val managedChannels = ConcurrentHashMap<Channel, KcpChannel>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val closed = AtomicBoolean(false)

    init {
        scope.launch {
            while (!isClosed()) {
                try {
                    // Accept a raw underlying channel (e.g., UdpChannel)
                    val rawChannel = underlyingServerChannel.accept()

                    // Wrap it in a KcpChannel. A conversation ID (conv) is needed.
                    // For a server, the conv should be determined from the client's first packet.
                    // This simplified example uses a placeholder. A real implementation
                    // would need to peek at the first packet to get the conv.
                    // A better approach is to have KcpChannel determine the conv from the first input.
                    val conv = System.currentTimeMillis() // Placeholder
                    val kcpChannel = KcpChannel(conv, rawChannel as UdpChannel, kcpOpt)

                    managedChannels[rawChannel] = kcpChannel
                    newKcpChannels.send(kcpChannel)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        // Underlying server channel was likely closed.
                        this@KcpServerChannel.close()
                    }
                    break
                }
            }
        }
    }

    override val localAddress get() = underlyingServerChannel.localAddress

    /**
     * Accepts a new incoming KcpChannel.
     */
    override suspend fun accept(): KcpChannel {
        return newKcpChannels.receive()
    }

    /**
     * Closes this server channel and the underlying one, plus all managed channels.
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            underlyingServerChannel.close()
            managedChannels.values.forEach { it.close() }
            newKcpChannels.close()
            scope.cancel()
        }
    }

    override fun isClosed(): Boolean {
        return closed.get()
    }
}