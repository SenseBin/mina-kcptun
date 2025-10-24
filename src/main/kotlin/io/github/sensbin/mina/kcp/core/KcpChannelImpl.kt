package io.github.sensbin.mina.kcp.core

import io.github.sensbin.mina.kcp.core.proto.KcpGlobal
import io.github.sensbin.mina.kcp.core.proto.SessionAttrKey
import io.github.sensbin.mina.kcp.util.Aes192
import kcp.KCP
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.service.IoHandlerAdapter
import org.apache.mina.core.session.IdleStatus
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.FilterEvent
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor
import org.apache.mina.transport.socket.nio.NioDatagramConnector
import org.apache.mina.transport.socket.nio.NioSocketAcceptor
import org.apache.mina.transport.socket.nio.NioSocketConnector
import org.slf4j.LoggerFactory
import java.net.SocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A KCP channel that wraps a UdpChannel to provide reliable, ordered communication.
 * It handles the KCP state machine, including sending, receiving, and updates.
 */
class KcpChannelImpl internal constructor(
    private val localAddress: SocketAddress,
    private val targetAddress: SocketAddress,
    private val acceptor: NioSocketAcceptor,
    private val kcpOpt: KcpOpt,
) : KcpChannel {
    private val logger = LoggerFactory.getLogger(KcpChannelImpl::class.java)

    private val closeMark = AtomicBoolean(false)
    private val closeFuture = CompletableFuture<KcpChannel>()

    private val key = Aes192.deriveKeyFromPassword(kcpOpt.secret)

    override fun close() {
        if (closeMark.compareAndSet(false, true)) {
            acceptor.unbind()
            closeFuture.complete(this)
        }
    }

    override fun isClosed(): Boolean {
        return closeMark.get()
    }

    override fun await(): Future<KcpChannel> {
        return closeFuture
    }

    internal fun getSessionHandler(): IoHandler {
        return IoHandler()
    }

    private fun setKcpOpt(kcp: KCP) {
        kcp.SetMtu(kcpOpt.mtu)
        kcp.WndSize(kcpOpt.sndWnd, kcpOpt.rcvWnd)
        kcp.NoDelay(kcpOpt.nodelay, kcpOpt.interval.toInt(), kcpOpt.resend, kcpOpt.nc)
    }

    inner class IoHandler() : IoHandlerAdapter() {
        override fun sessionOpened(session: IoSession?) {
            session!!.suspendRead()

            logger.info("tcp->kcp, connected from {}", session.remoteAddress)

            val downstreamConnector = NioDatagramConnector()
            downstreamConnector.handler = object : IoHandlerAdapter() {
                override fun sessionOpened(downstreamSession: IoSession?) {
                    downstreamSession!!
                    downstreamSession.setAttribute(SessionAttrKey.KCP_SERVER_DOWNSTREAM, downstreamSession)
                    session.resumeRead()


                    val kcp = object : KCP(1) {
                        override fun output(buffer: ByteArray, size: Int) {
                            val toSend = if (size == buffer.size) {
                                buffer
                            } else {
                                buffer.copyOfRange(0, size)
                            }
                            runCatching {
                                val encrypted = Aes192.encrypt(key, toSend)
                                val buff = IoBuffer.allocate(encrypted.size)
                                buff.put(encrypted, 0, encrypted.size)
                                buff.rewind()
                                downstreamSession.write(buff)
                            }
                        }
                    }
                    setKcpOpt(kcp)
                    downstreamSession.setAttribute(SessionAttrKey.KCP_CTRL_BLOCK, kcp)

                    fun updateFun() {
                        try {
                            kcp.Update(System.currentTimeMillis())
                            while (true) {
                                val readSize = kcp.PeekSize()
                                if (kcp.PeekSize() > 0) {
                                    val bytes = ByteArray(readSize)
                                    val recvSize = kcp.Recv(bytes)
                                    if (recvSize > 0) {
                                        val toSend = if (recvSize == readSize) {
                                            bytes
                                        } else {
                                            // partial read, should not happen
                                            bytes.copyOfRange(0, recvSize)
                                        }

                                        val buff = IoBuffer.allocate(toSend.size)
                                        buff.put(toSend, 0, toSend.size)
                                        buff.rewind()
                                        session.write(buff)

                                        logger.info("tcp->kcp, receive message, len={}", toSend.size)
                                        continue
                                    }
                                }
                                break
                            }
                        } catch (_: Exception) {
                        } finally {
                            if (!session.closeFuture.isClosed) {
                                KcpGlobal.KCP_IO_WORKER.schedule({ updateFun() }, kcpOpt.interval, TimeUnit.MILLISECONDS)
                            }
                        }
                    }
                    KcpGlobal.KCP_IO_WORKER.schedule({ updateFun() }, kcpOpt.interval, TimeUnit.MILLISECONDS)
                }

                override fun sessionClosed(downstreamSession: IoSession?) {
                    session.closeOnFlush()
                }

                override fun sessionIdle(downstreamSession: IoSession?, status: IdleStatus?) {
                }

                override fun exceptionCaught(downstreamSession: IoSession?, cause: Throwable?) {
                    session.closeOnFlush()
                }

                override fun messageReceived(downstreamSession: IoSession?, message: Any?) {
                    if (message is IoBuffer) {
                        val ioBuff = message
                        val size = ioBuff.remaining()
                        if (size > 0) {
                            val buffer = ByteArray(size)
                            ioBuff.get(buffer)
                            getKcp(downstreamSession!!).Input(buffer)
                        }
                    }
                }

                override fun messageSent(downstreamSession: IoSession?, message: Any?) {
                }

                override fun inputClosed(downstreamSession: IoSession?) {
                }

                override fun event(downstreamSession: IoSession?, event: FilterEvent?) {
                }
            }

            downstreamConnector.sessionConfig.also {
                it.isBroadcast = false
                it.isReuseAddress = false
                it.isCloseOnPortUnreachable = false
                it.receiveBufferSize = 4096
                it.sendBufferSize = 4096
            }
            downstreamConnector.connect(targetAddress)
        }

        override fun event(session: IoSession?, event: FilterEvent?) {
        }

        override fun inputClosed(session: IoSession?) {
            getDownstream(session!!).closeOnFlush()
        }

        override fun messageSent(session: IoSession?, message: Any?) {
        }

        override fun messageReceived(session: IoSession?, message: Any?) {
            val kcp = getKcp(session!!)
            if (message is IoBuffer) {
                val byteArray = ByteArray(message.remaining())
                message.get(byteArray)

                runCatching {
                    val decrypted = Aes192.decrypt(key, byteArray)
                    kcp.Input(decrypted)
                }
            }
        }

        override fun exceptionCaught(session: IoSession?, cause: Throwable?) {
            getDownstream(session!!).closeNow()
        }

        override fun sessionIdle(session: IoSession?, status: IdleStatus?) {
        }
    }

    private fun getDownstream(session: IoSession): IoSession {
        val downstreamSession = session.getAttribute(SessionAttrKey.KCP_SERVER_DOWNSTREAM)
        if (downstreamSession is IoSession) {
            return downstreamSession
        }
        throw Exception("downstream not found")
    }


    private fun getKcp(session: IoSession): KCP {
        val kcp = session.getAttribute(SessionAttrKey.KCP_CTRL_BLOCK)
        if (kcp is KCP) {
            return kcp
        }
        throw Exception("kcp ctrl block not found")
    }
}
