package io.github.sensbin.mina.kcp.core.proto

enum class Command(val cmdId: Byte) {
    PING(0),
    SYN(1),
    EOF(2),

    RST(-1),
}