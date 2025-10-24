package io.github.sensbin.mina.kcp.core

import java.util.concurrent.Future

interface KcpChannel {

    fun await(): Future<KcpChannel>

    /**
     * 关闭服务器通道。
     */
    fun close()

    /**
     * 检查服务器通道是否已关闭。
     * @return 如果已关闭则为 true
     */
    fun isClosed(): Boolean
}

