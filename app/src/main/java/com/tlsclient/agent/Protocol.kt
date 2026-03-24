package com.tlsclient.agent

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

object Protocol {

    private var msgCounter = 0

    fun nextId(): Int = ++msgCounter

    // ── Frame I/O ─────────────────────────────────────────────────────────────

    fun readExact(stream: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = stream.read(buf, offset, n - offset)
            if (read < 0) throw Exception("socket closed")
            offset += read
        }
        return buf
    }

    fun readFrame(stream: InputStream): ByteArray {
        val header = readExact(stream, 4)
        val length = ByteBuffer.wrap(header).int
        if (length == 0) return ByteArray(0)
        if (length > Config.MAX_FRAME_BYTES) throw Exception("frame too large: $length")
        return readExact(stream, length)
    }

    fun writeFrame(stream: OutputStream, payload: ByteArray) {
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        stream.write(header)
        stream.write(payload)
        stream.flush()
    }

    // ── Envelope send/receive ─────────────────────────────────────────────────

    fun sendEnvelope(stream: OutputStream, envelope: TlsProto.Envelope, compress: Boolean = true) {
        val raw = envelope.toByteArray()
        val payload = if (compress && raw.size >= Config.COMPRESS_THRESHOLD) {
            byteArrayOf(0x01) + zlibCompress(raw)
        } else {
            byteArrayOf(0x00) + raw
        }
        writeFrame(stream, payload)
    }

    fun recvEnvelope(stream: InputStream): TlsProto.Envelope {
        val frame = readFrame(stream)
        if (frame.isEmpty()) throw Exception("empty frame")
        val marker = frame[0]
        val data = frame.copyOfRange(1, frame.size)
        val decompressed = if (marker == 0x01.toByte()) zlibDecompress(data) else data
        return TlsProto.Envelope.parseFrom(decompressed)
    }

    fun makeEnv(clientId: String, group: String): TlsProto.Envelope {
        return TlsProto.Envelope.newBuilder()
            .setMsgId(nextId())
            .setClientId(clientId)
            .setGroup(group)
            .build()
    }

    // ── Compression ───────────────────────────────────────────────────────────

    private fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater(6)
        deflater.setInput(data)
        deflater.finish()
        val buf = ByteArray(data.size + 64)
        val len = deflater.deflate(buf)
        deflater.end()
        return buf.copyOf(len)
    }

    private fun zlibDecompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val buf = ByteArray(data.size * 4 + 1024)
        val len = inflater.inflate(buf)
        inflater.end()
        return buf.copyOf(len)
    }
}

// Helper extension
operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(this.size + other.size)
    this.copyInto(result)
    other.copyInto(result, this.size)
    return result
}
