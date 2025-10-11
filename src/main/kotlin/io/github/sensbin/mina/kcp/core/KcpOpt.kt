package io.github.sensbin.mina.kcp.core

/**
 * KCP-specific options.
 *
 * @property mtu Maximum Transmission Unit.
 * @property sndWnd Send window size.
 * @property rcvWnd Receive window size.
 * @property nodelay Whether to enable no-delay mode. 0 for disabled, 1 for enabled.
 * @property interval Internal update timer interval in milliseconds.
 * @property resend Fast resend. 0 for disabled, 1 for enabled.
 * @property nc No congestion control. 0 for normal, 1 for disabled.
 */
data class KcpOpt(
    val mtu: Int = KCP.IKCP_MTU_DEF,
    val sndWnd: Int = KCP.IKCP_WND_SND,
    val rcvWnd: Int = KCP.IKCP_WND_RCV,
    val nodelay: Int = 0,
    val interval: Int = KCP.IKCP_INTERVAL,
    val resend: Int = 0,
    val nc: Int = 0
)
