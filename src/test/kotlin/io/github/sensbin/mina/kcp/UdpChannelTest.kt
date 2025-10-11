package io.github.sensbin.mina.kcp

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UdpChannelTest {

    private val logger = LoggerFactory.getLogger(UdpChannelTest::class.java)
    private val serverPort = 18888
    private val serverAddress = InetSocketAddress("127.0.0.1", serverPort)
    private lateinit var serverChannel: UdpServerChannel
    private lateinit var serverJob: Job

    @BeforeAll
    fun setup() {
        serverChannel = UdpServerChannel(serverAddress)
        logger.info("UDP Echo Server started on port $serverPort")

        // 启动服务器协程
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive) {
                    val clientChannel = serverChannel.accept()
                    logger.info("Accepted connection from ${clientChannel.remoteAddress}")
                    // 为每个客户端启动一个协程来处理回显逻辑
                    launch {
                        handleClient(clientChannel)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.error("Server error", e)
                }
            } finally {
                logger.info("Server coroutine finished.")
            }
        }
    }

    private suspend fun handleClient(channel: Channel) {
        val buffer = ByteArray(1024)
        try {
            while (true) {
                val size = channel.read(buffer)
                if (size == -1) {
                    logger.info("Client ${channel.remoteAddress} disconnected.")
                    break
                }
                val receivedMsg = String(buffer, 0, size)
                logger.info("Server received: '$receivedMsg' from ${channel.remoteAddress}")

                val response = buffer.copyOfRange(0, size)
                channel.write(response)
                logger.info("Server echoed: '$receivedMsg' to ${channel.remoteAddress}")
            }
        } catch (e: Exception) {
            logger.warn("Error handling client ${channel.remoteAddress}", e)
        } finally {
            channel.close()
        }
    }

    @AfterAll
    fun tearDown() {
        logger.info("Shutting down server...")
        serverJob.cancel()
        serverChannel.close()
        logger.info("Server shut down.")
    }

    @Test
    fun `test echo server with multiple clients`() = runTest() {
        val clientCount = 3
        val jobs = mutableListOf<Job>()

        // 启动多个客户端并发测试
        repeat(clientCount) { i ->
            val job = launch(Dispatchers.IO) {
                val clientName = "client-$i"
                var clientChannel: Channel? = null
                try {
                    clientChannel = UdpChannel.connect(serverAddress)
                    logger.info("[$clientName] connected to server.")

                    val message = "Hello from $clientName"
                    clientChannel.write(message.toByteArray())
                    logger.info("[$clientName] sent: '$message'")

                    val readBuffer = ByteArray(1024)
                    val size = clientChannel.read(readBuffer)
                    val receivedMessage = String(readBuffer, 0, size)
                    logger.info("[$clientName] received: '$receivedMessage'")

                    assertEquals(message, receivedMessage)
                } catch (e: Exception) {
                    logger.error("[$clientName] error", e)
                    throw e
                } finally {
                    clientChannel?.close()
                    logger.info("[$clientName] connection closed.")
                }
            }
            jobs.add(job)
        }

        // 等待所有客户端任务完成
        jobs.joinAll()
        logger.info("All clients finished.")
    }
}

