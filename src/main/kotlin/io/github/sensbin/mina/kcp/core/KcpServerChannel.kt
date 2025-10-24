package io.github.sensbin.mina.kcp.core

/**
 * 抽象服务器通道接口，用于监听和接受传入的连接。
 */
interface KcpServerChannel {
    /**
     * 阻塞等待并接受一个新的 Channel。
     * @return 代表新连接的 Channel
     */
    suspend fun accept(): KcpChannel

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

