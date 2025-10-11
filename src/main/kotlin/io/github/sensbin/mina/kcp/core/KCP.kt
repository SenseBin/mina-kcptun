package io.github.sensbin.mina.kcp.core

import kotlin.math.max
import kotlin.math.min

abstract class KCP(val conv: Long) {

    companion object {
        const val IKCP_RTO_NDL = 30 // no delay min rto
        const val IKCP_RTO_MIN = 100 // normal min rto
        const val IKCP_RTO_DEF = 200
        const val IKCP_RTO_MAX = 60000
        const val IKCP_CMD_PUSH = 81 // cmd: push data
        const val IKCP_CMD_ACK = 82 // cmd: ack
        const val IKCP_CMD_WASK = 83 // cmd: window probe (ask)
        const val IKCP_CMD_WINS = 84 // cmd: window size (tell)
        const val IKCP_ASK_SEND = 1 // need to send IKCP_CMD_WASK
        const val IKCP_ASK_TELL = 2 // need to send IKCP_CMD_WINS
        const val IKCP_WND_SND = 32
        const val IKCP_WND_RCV = 32
        const val IKCP_MTU_DEF = 1400
        const val IKCP_ACK_FAST = 3
        const val IKCP_INTERVAL = 100
        const val IKCP_OVERHEAD = 24
        const val IKCP_DEADLINK = 10
        const val IKCP_THRESH_INIT = 2
        const val IKCP_THRESH_MIN = 2
        const val IKCP_PROBE_INIT = 7000 // 7 secs to probe window size
        const val IKCP_PROBE_LIMIT = 120000 // up to 120 secs to probe window

        fun ikcp_encode8u(p: ByteArray, offset: Int, c: Byte) {
            p[offset] = c
        }

        fun ikcp_decode8u(p: ByteArray, offset: Int): Byte {
            return p[offset]
        }

        fun ikcp_encode16u(p: ByteArray, offset: Int, w: Int) {
            p[offset + 0] = (w shr 8).toByte()
            p[offset + 1] = (w shr 0).toByte()
        }

        fun ikcp_decode16u(p: ByteArray, offset: Int): Int {
            return (p[offset + 0].toInt() and 0xFF shl 8) or
                    (p[offset + 1].toInt() and 0xFF)
        }

        fun ikcp_encode32u(p: ByteArray, offset: Int, l: Long) {
            p[offset + 0] = (l shr 24).toByte()
            p[offset + 1] = (l shr 16).toByte()
            p[offset + 2] = (l shr 8).toByte()
            p[offset + 3] = (l shr 0).toByte()
        }

        fun ikcp_decode32u(p: ByteArray, offset: Int): Long {
            return (p[offset + 0].toLong() and 0xFFL shl 24) or
                    (p[offset + 1].toLong() and 0xFFL shl 16) or
                    (p[offset + 2].toLong() and 0xFFL shl 8) or
                    (p[offset + 3].toLong() and 0xFFL)
        }

        fun <T> slice(list: MutableList<T>, start: Int, stop: Int) {
            val count = stop - start
            if (count <= 0) return
            for (i in 0 until list.size - count) {
                list[i] = list[i + count]
            }
            repeat(count) {
                list.removeAt(list.size - 1)
            }
        }

        internal fun _itimediff(later: Long, earlier: Long): Int {
            return (later - earlier).toInt()
        }
    }

    private class Segment(size: Int) {
        var conv: Long = 0
        var cmd: Long = 0
        var frg: Long = 0
        var wnd: Long = 0
        var ts: Long = 0
        var sn: Long = 0
        var una: Long = 0
        var resendts: Long = 0
        var rto: Long = 0
        var fastack: Long = 0
        var xmit: Long = 0
        var data: ByteArray = ByteArray(size)

        fun encode(ptr: ByteArray, offset: Int): Int {
            val offset_ = offset
            var currentOffset = offset

            ikcp_encode32u(ptr, currentOffset, conv)
            currentOffset += 4
            ikcp_encode8u(ptr, currentOffset, cmd.toByte())
            currentOffset += 1
            ikcp_encode8u(ptr, currentOffset, frg.toByte())
            currentOffset += 1
            ikcp_encode16u(ptr, currentOffset, wnd.toInt())
            currentOffset += 2
            ikcp_encode32u(ptr, currentOffset, ts)
            currentOffset += 4
            ikcp_encode32u(ptr, currentOffset, sn)
            currentOffset += 4
            ikcp_encode32u(ptr, currentOffset, una)
            currentOffset += 4
            ikcp_encode32u(ptr, currentOffset, data.size.toLong())
            currentOffset += 4

            return currentOffset - offset_
        }
    }

    private var snd_una: Long = 0
    private var snd_nxt: Long = 0
    private var rcv_nxt: Long = 0
    private var ts_recent: Long = 0
    private var ts_lastack: Long = 0
    private var ts_probe: Long = 0
    private var probe_wait: Long = 0
    private var snd_wnd: Long = IKCP_WND_SND.toLong()
    private var rcv_wnd: Long = IKCP_WND_RCV.toLong()
    private var rmt_wnd: Long = IKCP_WND_RCV.toLong()
    private var cwnd: Long = 0
    private var incr: Long = 0
    private var probe: Long = 0
    private var mtu: Long = IKCP_MTU_DEF.toLong()
    private var mss: Long = this.mtu - IKCP_OVERHEAD
    private var buffer: ByteArray = ByteArray((mtu + IKCP_OVERHEAD).toInt() * 3)
    private val nrcv_buf = mutableListOf<Segment>()
    private val nsnd_buf = mutableListOf<Segment>()
    private val nrcv_que = mutableListOf<Segment>()
    private val nsnd_que = mutableListOf<Segment>()
    var state: Long = 0
        private set
    private val acklist = mutableListOf<Long>()
    private var rx_srtt: Long = 0
    private var rx_rttval: Long = 0
    private var rx_rto: Long = IKCP_RTO_DEF.toLong()
    private var rx_minrto: Long = IKCP_RTO_MIN.toLong()
    private var current: Long = 0
    private var interval: Long = IKCP_INTERVAL.toLong()
    private var ts_flush: Long = IKCP_INTERVAL.toLong()
    private var nodelay: Long = 0
    private var updated: Long = 0
    private var logmask: Long = 0
    private var ssthresh: Long = IKCP_THRESH_INIT.toLong()
    private var fastresend: Long = 0
    private var nocwnd: Long = 0
    private var xmit: Long = 0
    private var dead_link: Long = IKCP_DEADLINK.toLong()

    protected abstract fun output(buffer: ByteArray, size: Int)

    fun recv(buffer: ByteArray): Int {
        if (nrcv_que.isEmpty()) {
            return -1
        }

        val peekSize = peekSize()
        if (peekSize < 0) {
            return -2
        }

        if (peekSize > buffer.size) {
            return -3
        }

        val recover = nrcv_que.size >= rcv_wnd

        var count = 0
        var n = 0
        val iter = nrcv_que.iterator()
        while(iter.hasNext()) {
            val seg = iter.next()
            System.arraycopy(seg.data, 0, buffer, n, seg.data.size)
            n += seg.data.size
            count++
            iter.remove()
            if (seg.frg == 0L) {
                break
            }
        }

        val toRemove = mutableListOf<Segment>()
        val nrcvBufIter = nrcv_buf.iterator()
        while(nrcvBufIter.hasNext()){
            val seg = nrcvBufIter.next()
            if (seg.sn == rcv_nxt && nrcv_que.size < rcv_wnd) {
                rcv_nxt++
                nrcv_que.add(seg)
                nrcvBufIter.remove()
            } else {
                break
            }
        }

        if (nrcv_que.size < rcv_wnd && recover) {
            probe = probe or IKCP_ASK_TELL.toLong()
        }

        return n
    }

    fun peekSize(): Int {
        if (nrcv_que.isEmpty()) {
            return -1
        }

        val seq = nrcv_que[0]

        if (seq.frg == 0L) {
            return seq.data.size
        }

        if (nrcv_que.size < seq.frg + 1) {
            return -1
        }

        var length = 0
        for (item in nrcv_que) {
            length += item.data.size
            if (item.frg == 0L) {
                break
            }
        }

        return length
    }

    fun send(buffer: ByteArray): Int {
        if (buffer.isEmpty()) {
            return -1
        }

        val count = if (buffer.size < mss) {
            1
        } else {
            (buffer.size + mss - 1) / mss
        }.toInt()

        if (count > 255) {
            return -2
        }

        if (count == 0) {
            return 0
        }

        var offset = 0
        var remaining = buffer.size
        for (i in 0 until count) {
            val size = min(remaining, mss.toInt())
            val seg = Segment(size)
            System.arraycopy(buffer, offset, seg.data, 0, size)
            offset += size
            seg.frg = (count - i - 1).toLong()
            nsnd_que.add(seg)
            remaining -= size
        }
        return 0
    }

    private fun update_ack(rtt: Int) {
        if (rx_srtt == 0L) {
            rx_srtt = rtt.toLong()
            rx_rttval = (rtt / 2).toLong()
        } else {
            var delta = rtt - rx_srtt
            if (delta < 0) {
                delta = -delta
            }
            rx_rttval = (3 * rx_rttval + delta) / 4
            rx_srtt = (7 * rx_srtt + rtt) / 8
            if (rx_srtt < 1) {
                rx_srtt = 1
            }
        }
        val rto = rx_srtt + max(1, 4 * rx_rttval)
        rx_rto = rto.coerceIn(rx_minrto, IKCP_RTO_MAX.toLong())
    }

    private fun shrink_buf() {
        snd_una = nsnd_buf.firstOrNull()?.sn ?: snd_nxt
    }

    private fun parse_ack(sn: Long) {
        if (_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0) {
            return
        }

        val iter = nsnd_buf.iterator()
        while (iter.hasNext()) {
            val seg = iter.next()
            if (_itimediff(sn, seg.sn) < 0) {
                break
            }
            seg.fastack++
            if (sn == seg.sn) {
                iter.remove()
                break
            }
        }
    }

    private fun parse_una(una: Long) {
        var count = 0
        for (seg in nsnd_buf) {
            if (_itimediff(una, seg.sn) > 0) {
                count++
            } else {
                break
            }
        }
        if (count > 0) {
            slice(nsnd_buf, 0, count)
        }
    }

    private fun ack_push(sn: Long, ts: Long) {
        acklist.add(sn)
        acklist.add(ts)
    }

    private fun parse_data(newseg: Segment) {
        val sn = newseg.sn
        if (_itimediff(sn, rcv_nxt + rcv_wnd) >= 0 || _itimediff(sn, rcv_nxt) < 0) {
            return
        }

        var repeat = false
        var insert_idx = -1
        for (i in nrcv_buf.indices.reversed()) {
            val seg = nrcv_buf[i]
            if (seg.sn == sn) {
                repeat = true
                break
            }
            if (_itimediff(sn, seg.sn) > 0) {
                insert_idx = i
                break
            }
        }

        if (!repeat) {
            if (insert_idx == -1) {
                nrcv_buf.add(0, newseg)
            } else {
                nrcv_buf.add(insert_idx + 1, newseg)
            }
        }

        val iter = nrcv_buf.iterator()
        while(iter.hasNext()){
            val seg = iter.next()
            if (seg.sn == rcv_nxt && nrcv_que.size < rcv_wnd) {
                rcv_nxt++
                nrcv_que.add(seg)
                iter.remove()
            } else {
                break
            }
        }
    }

    fun input(data: ByteArray): Int {
        val s_una = snd_una
        if (data.size < IKCP_OVERHEAD) {
            return 0
        }

        var offset = 0
        while (true) {
            if (data.size - offset < IKCP_OVERHEAD) {
                break
            }

            val conv_ = ikcp_decode32u(data, offset)
            if (conv != conv_) {
                return -1
            }
            offset += 4

            val cmd = ikcp_decode8u(data, offset).toLong()
            offset += 1
            val frg = ikcp_decode8u(data, offset).toLong()
            offset += 1
            val wnd = ikcp_decode16u(data, offset)
            offset += 2
            val ts = ikcp_decode32u(data, offset)
            offset += 4
            val sn = ikcp_decode32u(data, offset)
            offset += 4
            val una = ikcp_decode32u(data, offset)
            offset += 4
            val length = ikcp_decode32u(data, offset)
            offset += 4

            if (data.size - offset < length) {
                return -2
            }

            if (cmd != IKCP_CMD_PUSH.toLong() && cmd != IKCP_CMD_ACK.toLong() && cmd != IKCP_CMD_WASK.toLong() && cmd != IKCP_CMD_WINS.toLong()) {
                return -3
            }

            rmt_wnd = wnd.toLong()
            parse_una(una)
            shrink_buf()

            when (cmd) {
                IKCP_CMD_ACK.toLong() -> {
                    if (_itimediff(current, ts) >= 0) {
                        update_ack(_itimediff(current, ts))
                    }
                    parse_ack(sn)
                    shrink_buf()
                }
                IKCP_CMD_PUSH.toLong() -> {
                    if (_itimediff(sn, rcv_nxt + rcv_wnd) < 0) {
                        ack_push(sn, ts)
                        if (_itimediff(sn, rcv_nxt) >= 0) {
                            val seg = Segment(length.toInt())
                            seg.conv = conv_
                            seg.cmd = cmd
                            seg.frg = frg
                            seg.wnd = wnd.toLong()
                            seg.ts = ts
                            seg.sn = sn
                            seg.una = una
                            if (length > 0) {
                                System.arraycopy(data, offset, seg.data, 0, length.toInt())
                            }
                            parse_data(seg)
                        }
                    }
                }
                IKCP_CMD_WASK.toLong() -> {
                    probe = probe or IKCP_ASK_TELL.toLong()
                }
                IKCP_CMD_WINS.toLong() -> {
                    // do nothing
                }
                else -> return -3
            }
            offset += length.toInt()
        }

        if (_itimediff(snd_una, s_una) > 0) {
            if (cwnd < rmt_wnd) {
                val mss_ = mss
                if (cwnd < ssthresh) {
                    cwnd++
                    incr += mss_
                } else {
                    if (incr < mss_) {
                        incr = mss_
                    }
                    incr += (mss_ * mss_) / incr + (mss_ / 16)
                    if ((cwnd + 1) * mss_ <= incr) {
                        cwnd++
                    }
                }
                if (cwnd > rmt_wnd) {
                    cwnd = rmt_wnd
                    incr = rmt_wnd * mss_
                }
            }
        }
        return 0
    }

    private fun wnd_unused(): Int {
        return if (nrcv_que.size < rcv_wnd) {
            (rcv_wnd - nrcv_que.size).toInt()
        } else 0
    }

    private fun flush() {
        val current_ = current
        if (updated == 0L) {
            return
        }

        val seg = Segment(0)
        seg.conv = conv
        seg.cmd = IKCP_CMD_ACK.toLong()
        seg.wnd = wnd_unused().toLong()
        seg.una = rcv_nxt

        var offset = 0
        val ackcount = acklist.size / 2
        for (i in 0 until ackcount) {
            if (offset + IKCP_OVERHEAD > mtu) {
                output(buffer, offset)
                offset = 0
            }
            seg.sn = acklist[i * 2]
            seg.ts = acklist[i * 2 + 1]
            offset += seg.encode(buffer, offset)
        }
        acklist.clear()

        if (rmt_wnd == 0L) {
            if (probe_wait == 0L) {
                probe_wait = IKCP_PROBE_INIT.toLong()
                ts_probe = current + probe_wait
            } else {
                if (_itimediff(current, ts_probe) >= 0) {
                    if (probe_wait < IKCP_PROBE_INIT) {
                        probe_wait = IKCP_PROBE_INIT.toLong()
                    }
                    probe_wait += probe_wait / 2
                    if (probe_wait > IKCP_PROBE_LIMIT) {
                        probe_wait = IKCP_PROBE_LIMIT.toLong()
                    }
                    ts_probe = current + probe_wait
                    probe = probe or IKCP_ASK_SEND.toLong()
                }
            }
        } else {
            ts_probe = 0
            probe_wait = 0
        }

        if ((probe and IKCP_ASK_SEND.toLong()) != 0L) {
            seg.cmd = IKCP_CMD_WASK.toLong()
            if (offset + IKCP_OVERHEAD > mtu) {
                output(buffer, offset)
                offset = 0
            }
            offset += seg.encode(buffer, offset)
        }

        if ((probe and IKCP_ASK_TELL.toLong()) != 0L) {
            seg.cmd = IKCP_CMD_WINS.toLong()
            if (offset + IKCP_OVERHEAD > mtu) {
                output(buffer, offset)
                offset = 0
            }
            offset += seg.encode(buffer, offset)
        }
        probe = 0

        var cwnd_ = min(snd_wnd, rmt_wnd)
        if (nocwnd == 0L) {
            cwnd_ = min(cwnd, cwnd_)
        }

        val toMove = mutableListOf<Segment>()
        val nsndQueIter = nsnd_que.iterator()
        while(nsndQueIter.hasNext()){
            val newseg = nsndQueIter.next()
            if (_itimediff(snd_nxt, snd_una + cwnd_) >= 0) {
                break
            }
            newseg.conv = conv
            newseg.cmd = IKCP_CMD_PUSH.toLong()
            newseg.wnd = seg.wnd
            newseg.ts = current_
            newseg.sn = snd_nxt
            newseg.una = rcv_nxt
            newseg.resendts = current_
            newseg.rto = rx_rto
            newseg.fastack = 0
            newseg.xmit = 0
            nsnd_buf.add(newseg)
            snd_nxt++
            nsndQueIter.remove()
        }

        val resent = if (fastresend > 0) fastresend else 0xFFFFFFFF
        val rtomin = if (nodelay == 0L) (rx_rto shr 3) else 0

        var change = 0
        var lost = 0

        for (segment in nsnd_buf) {
            var needsend = false
            if (segment.xmit == 0L) {
                needsend = true
                segment.xmit++
                segment.rto = rx_rto
                segment.resendts = current_ + segment.rto + rtomin
            } else if (_itimediff(current_, segment.resendts) >= 0) {
                needsend = true
                segment.xmit++
                xmit++
                if (nodelay == 0L) {
                    segment.rto += rx_rto
                } else {
                    segment.rto += rx_rto / 2
                }
                segment.resendts = current_ + segment.rto
                lost = 1
            } else if (segment.fastack >= resent) {
                needsend = true
                segment.xmit++
                segment.fastack = 0
                segment.resendts = current_ + segment.rto
                change++
            }

            if (needsend) {
                segment.ts = current_
                segment.wnd = seg.wnd
                segment.una = rcv_nxt

                val need = IKCP_OVERHEAD + segment.data.size
                if (offset + need >= mtu) {
                    output(buffer, offset)
                    offset = 0
                }

                offset += segment.encode(buffer, offset)
                if (segment.data.isNotEmpty()) {
                    System.arraycopy(segment.data, 0, buffer, offset, segment.data.size)
                    offset += segment.data.size
                }

                if (segment.xmit >= dead_link) {
                    state = -1
                }
            }
        }

        if (offset > 0) {
            output(buffer, offset)
        }

        if (change != 0) {
            val inflight = snd_nxt - snd_una
            ssthresh = inflight / 2
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN.toLong()
            }
            cwnd = ssthresh + resent
            incr = cwnd * mss
        }

        if (lost != 0) {
            ssthresh = cwnd / 2
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN.toLong()
            }
            cwnd = 1
            incr = mss
        }

        if (cwnd < 1) {
            cwnd = 1
            incr = mss
        }
    }

    fun update(current_: Long) {
        current = current_
        if (updated == 0L) {
            updated = 1
            ts_flush = current
        }

        var slap = _itimediff(current, ts_flush)
        if (slap >= 10000 || slap < -10000) {
            ts_flush = current
            slap = 0
        }

        if (slap >= 0) {
            ts_flush += interval
            if (_itimediff(current, ts_flush) >= 0) {
                ts_flush = current + interval
            }
            flush()
        }
    }

    fun check(current_: Long): Long {
        if (updated == 0L) {
            return current_
        }

        var ts_flush_ = ts_flush
        var tm_flush = 0x7fffffffL
        var tm_packet = 0x7fffffffL

        if (_itimediff(current_, ts_flush_) >= 10000 || _itimediff(current_, ts_flush_) < -10000) {
            ts_flush_ = current_
        }

        if (_itimediff(current_, ts_flush_) >= 0) {
            return current_
        }

        tm_flush = _itimediff(ts_flush_, current_).toLong()

        for (seg in nsnd_buf) {
            val diff = _itimediff(seg.resendts, current_)
            if (diff <= 0) {
                return current_
            }
            if (diff < tm_packet) {
                tm_packet = diff.toLong()
            }
        }

        val minimal = min(tm_packet, tm_flush)
        if (minimal >= interval) {
            return current_ + interval
        }
        return current_ + minimal
    }

    fun setMtu(mtu_: Int): Int {
        if (mtu_ < 50 || mtu_ < IKCP_OVERHEAD) {
            return -1
        }
        val newBuffer = ByteArray((mtu_ + IKCP_OVERHEAD) * 3)
        mtu = mtu_.toLong()
        mss = mtu - IKCP_OVERHEAD
        buffer = newBuffer
        return 0
    }

    fun interval(interval_: Int): Int {
        interval = interval_.coerceIn(10, 5000).toLong()
        return 0
    }

    fun noDelay(nodelay_: Int, interval_: Int, resend_: Int, nc_: Int): Int {
        if (nodelay_ >= 0) {
            nodelay = nodelay_.toLong()
            rx_minrto = if (nodelay != 0L) {
                IKCP_RTO_NDL.toLong()
            } else {
                IKCP_RTO_MIN.toLong()
            }
        }

        if (interval_ >= 0) {
            interval = interval_.coerceIn(10, 5000).toLong()
        }

        if (resend_ >= 0) {
            fastresend = resend_.toLong()
        }

        if (nc_ >= 0) {
            nocwnd = nc_.toLong()
        }
        return 0
    }

    fun wndSize(sndwnd: Int, rcvwnd: Int): Int {
        if (sndwnd > 0) {
            snd_wnd = sndwnd.toLong()
        }
        if (rcvwnd > 0) {
            rcv_wnd = rcvwnd.toLong()
        }
        return 0
    }

    fun waitSnd(): Int {
        return nsnd_buf.size + nsnd_que.size
    }
}
