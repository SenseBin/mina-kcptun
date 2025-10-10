# Mina-KCP Tunnel Library

## Overview
This is a Kotlin-based tunnel library leveraging the KCP protocol for reliable UDP transport. It uses Apache MINA as the underlying IO framework for cross-platform compatibility (PC and Android). The library is designed in layers:
- **Layer 1**: Abstract `Channel` interface and MINA-based UDP implementations (`UdpChannel` and `UdpServerChannel`).
- **Layer 2**: KCP-wrapped channels (TBD).

The current version focuses on Layer 1, providing coroutine-based blocking IO operations.

## Features
- Coroutine-friendly API with suspend functions for read/write/close.
- UDP server support with `accept()` for incoming connections.
- Compatible with JVM 17 (PC) and adaptable for Android.
- Extensible with MINA filters for protocol codecs.

## Installation
Add to your `build.gradle.kts`:
```kotlin
dependencies {
    implementation("io.github.sensbin.mina.kcp:mina-kcp:1.0-SNAPSHOT")
}