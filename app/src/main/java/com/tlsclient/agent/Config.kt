package com.tlsclient.agent

object Config {
    // ── Server connection ─────────────────────────────────────────────────────
    const val SERVER_HOST = "johnthehecker143-47209.portmap.host"
    const val SERVER_PORT = 47209
    const val SERVER_NAME = "localhost"   // must match cert CN
    const val AUTH_TOKEN  = "changeme-token-1234"
    const val GROUP       = "android"

    // ── Reconnect backoff ─────────────────────────────────────────────────────
    const val INITIAL_DELAY_MS = 1_000L
    const val MAX_DELAY_MS     = 60_000L
    const val MULTIPLIER       = 2.0

    // ── Protocol ─────────────────────────────────────────────────────────────
    const val COMPRESS_THRESHOLD = 512
    const val MAX_FRAME_BYTES    = 50 * 1024 * 1024

    // ── Notification ─────────────────────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID = "agent_channel"
    const val NOTIFICATION_ID         = 1001
}
