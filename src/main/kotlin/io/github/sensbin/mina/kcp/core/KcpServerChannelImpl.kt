package io.github.sensbin.mina.kcp.core

import org.apache.mina.core.service.IoHandlerAdapter
import org.apache.mina.core.session.IdleStatus
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.FilterEvent
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * A server-side channel to produce KcpChannels.
 */
class KcpServerChannelImpl internal constructor(
    private val localAddress: SocketAddress,
    private val acceptor: NioDatagramAcceptor,
    private val kcpOpt: KcpOpt,
) : KcpServerChannel {

    companion object {
        private val EXECUTOR: ScheduledExecutorService = ScheduledThreadPoolExecutor(
            max(4, Runtime.getRuntime().availableProcessors()),
            object : ThreadFactory {
                private val counter = AtomicLong(0)
                override fun newThread(r: Runnable): Thread {
                    return Thread(r, "worker-${counter.getAndIncrement()}@kcp-server-channel")
                }
            }
        )
    }

    private val closeMark = AtomicBoolean(false)
    private val channelDict = ConcurrentHashMap<KcpChannelKey, KcpChannelControlBlock>()

    init {
        EXECUTOR
    }

    override suspend fun accept(): KcpChannelImpl {
        TODO("Not yet implemented")
    }

    override fun close() {
        if (closeMark.compareAndSet(false, true)) {
            acceptor.unbind()
        }
    }

    override fun isClosed(): Boolean {
        return closeMark.get()
    }

    internal fun getSessionHandler(): IoHandler {
        return IoHandler()
    }

    inner class IoHandler : IoHandlerAdapter() {
        override fun sessionCreated(session: IoSession?) {
            super.sessionCreated(session)
        }

        override fun sessionOpened(session: IoSession?) {
            super.sessionOpened(session)
        }

        override fun sessionClosed(session: IoSession?) {
            super.sessionClosed(session)
        }

        override fun sessionIdle(session: IoSession?, status: IdleStatus?) {
            super.sessionIdle(session, status)
        }

        override fun exceptionCaught(session: IoSession?, cause: Throwable?) {
            super.exceptionCaught(session, cause)
        }

        override fun messageReceived(session: IoSession?, message: Any?) {
            super.messageReceived(session, message)
        }

        override fun messageSent(session: IoSession?, message: Any?) {
            super.messageSent(session, message)
        }

        override fun inputClosed(session: IoSession?) {
            super.inputClosed(session)
        }

        override fun event(session: IoSession?, event: FilterEvent?) {
            super.event(session, event)
        }
    }

    data class KcpChannelKey(
        val clientId1: Long,
        val clientId2: Long,
        val channelId1: Long,
        val channelId2: Long,
    )

    data class KcpChannelControlBlock(
        val kcpChannelKey: KcpChannelKey,
        val kcpChannel: KcpChannelImpl,
    )
}