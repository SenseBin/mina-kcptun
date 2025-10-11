package io.github.sensbin.mina.kcp

import java.net.SocketAddress


/**
 * 抽象通道接口，代表一个 IO 端点。
 * 支持协程阻塞式读写操作。
 */
interface Channel {

    /**
     * 远程地址
     */
    val remoteAddress: SocketAddress

    /**
     * 本地地址
     */
    val localAddress: SocketAddress

    /**
     * 从通道读取数据到缓冲区。
     * @param buffer 目标缓冲区
     * @return 实际读取字节数，如果通道已关闭则为-1
     */
    suspend fun read(buffer: ByteArray): Int

    /**
     * 将缓冲区的数据写入通道。
     * @param buffer 源缓冲区
     * @return 实际写入字节数
     */
    suspend fun write(buffer: ByteArray): Int

    /**
     * 关闭通道。
     */
    fun close()

    /**
     * 是否已关闭。
     */
    fun isClosed(): Boolean
}