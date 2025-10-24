package io.github.sensbin.mina.kcp.core.proto


import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

object SessionAttrKey {
    public val KCP_CTRL_BLOCK = "KCP_CTRL_BLOCK"
    public val KCP_SERVER_DOWNSTREAM = "KCP_SERVER_DOWNSTREAM"
}

enum class Command(val cmdId: Byte) {
    PING(0),
    SYN(1),
    EOF(2),
    SEND(3),

    RST(-1),
}

enum class CompressMark(val compressMarkId: Byte) {
    NO_COMPRESS(0),
    SNAPPY(1),
}

enum class ChannelStatus {
    HELLO,
    CONNECT,
    EOF,
    CLOSE,
}

object KcpGlobal {
    public val KCP_IO_WORKER = ScheduledThreadPoolExecutor(
        max(4, Runtime.getRuntime().availableProcessors()),
        object : ThreadFactory {
            private val counter = AtomicLong(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "kcp-io-worker-${counter.getAndIncrement()}")
            }
        }
    )
}