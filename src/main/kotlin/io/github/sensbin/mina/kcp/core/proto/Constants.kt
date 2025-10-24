package io.github.sensbin.mina.kcp.core.proto

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