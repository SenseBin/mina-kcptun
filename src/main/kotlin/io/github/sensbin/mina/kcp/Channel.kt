package io.github.sensbin.mina.kcp


/**
 * 抽象通道接口，代表一个 IO 端点。
 * 支持协程阻塞式读写操作。
 */
interface Channel {
    /**
     * 读取数据到缓冲区，返回实际读取字节数。
     * @param buffer 目标缓冲区
     * @param offset 偏移量
     * @param length 最大读取长度
     * @return 实际读取字节数
     */
    suspend fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int

    /**
     * 写入数据从缓冲区。
     * @param buffer 源缓冲区
     * @param offset 偏移量
     * @param length 写入长度
     * @return 实际写入字节数
     */
    suspend fun write(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int

    /**
     * 关闭通道。
     */
    suspend fun close()

    /**
     * 是否已关闭。
     */
    fun isClosed(): Boolean
}