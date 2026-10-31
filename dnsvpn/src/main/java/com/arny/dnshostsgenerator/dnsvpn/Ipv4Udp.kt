package com.arny.dnshostsgenerator.dnsvpn

/**
 * Разбор и сборка IPv4+UDP пакетов, которые приходят на TUN-интерфейс.
 *
 * Своего кода здесь ровно столько, сколько нужно: TUN отдаёт IP-пакеты без заголовка
 * канального уровня, поэтому DNS-запрос — это IPv4 + UDP + payload.
 */
object Ipv4Udp {

    const val PROTOCOL_UDP = 17

    data class Packet(
        val sourceIp: ByteArray,
        val destinationIp: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val payload: ByteArray,
    )

    /** Минимальный размер: IPv4 (20) + UDP (8). */
    private const val HEADER_SIZE = 28

    /** Возвращает null, если пакет не IPv4/UDP или обрезан. */
    fun parse(buffer: ByteArray, length: Int): Packet? {
        if (length < HEADER_SIZE) return null
        val version = (buffer[0].toInt() and 0xF0) shr 4
        if (version != 4) return null
        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || length < headerLength + 8) return null
        if (buffer[9].toInt() != PROTOCOL_UDP) return null

        val sourceIp = buffer.copyOfRange(12, 16)
        val destinationIp = buffer.copyOfRange(16, 20)
        val sourcePort = u16(buffer, headerLength)
        val destinationPort = u16(buffer, headerLength + 2)
        val udpLength = u16(buffer, headerLength + 4)
        if (udpLength < 8) return null
        val payloadEnd = minOf(length, headerLength + udpLength)
        if (payloadEnd <= headerLength + 8) return null
        val payload = buffer.copyOfRange(headerLength + 8, payloadEnd)
        return Packet(sourceIp, destinationIp, sourcePort, destinationPort, payload)
    }

    /** Собирает ответный IPv4+UDP пакет: меняем адреса и порты местами. */
    fun buildReply(request: Packet, payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45                       // IPv4, заголовок 20 байт
        packet[1] = 0                          // DSCP/ECN
        putU16(packet, 2, totalLength)
        putU16(packet, 4, 0)                   // общий ID пока не нужен
        putU16(packet, 6, 0x4000)              // DF
        packet[8] = 64                         // TTL
        packet[9] = PROTOCOL_UDP.toByte()
        putU16(packet, 10, 0)                  // контрольная сумма IP считается ниже
        request.destinationIp.copyInto(packet, 12)
        request.sourceIp.copyInto(packet, 16)

        putU16(packet, 20, request.destinationPort)
        putU16(packet, 22, request.sourcePort)
        putU16(packet, 24, udpLength)
        putU16(packet, 26, 0)                  // контрольная сумма UDP считается ниже
        payload.copyInto(packet, 28)

        putU16(packet, 10, checksum(packet, 0, 20))
        putU16(packet, 26, udpChecksum(packet, payload))
        return packet
    }

    private fun u16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun putU16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun checksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i + 1 < offset + length) {
            sum += u16(buffer, i).toLong()
            i += 2
        }
        if (i < offset + length) sum += (buffer[i].toInt() and 0xFF).toLong() shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    /** UDP-контрольная сумма считается по псевдозаголовку; 0 означает «не считалась». */
    private fun udpChecksum(packet: ByteArray, payload: ByteArray): Int {
        var sum = 0L
        // Псевдозаголовок: src, dst, protocol, длина UDP.
        for (i in 12..19 step 2) sum += u16(packet, i).toLong()
        sum += PROTOCOL_UDP.toLong()
        sum += (8 + payload.size).toLong()
        var i = 20
        while (i + 1 < 20 + 8) {
            sum += u16(packet, i).toLong()
            i += 2
        }
        i = 0
        while (i + 1 < payload.size) {
            sum += ((payload[i].toInt() and 0xFF) shl 8 or (payload[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (i < payload.size) sum += (payload[i].toInt() and 0xFF).toLong() shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val result = (sum.inv() and 0xFFFF).toInt()
        return if (result == 0) 0xFFFF else result
    }
}
