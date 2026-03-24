package com.tlsclient.agent

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start the background service
        val intent = Intent(this, AgentService::class.java)
        startForegroundService(intent)
        // Immediately finish — no UI shown
        finish()
    }
}
