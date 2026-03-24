package com.tlsclient.agent

import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessageLite

// This file contains manually mapped Kotlin wrappers that match
// the Python messages_pb2.py schema exactly.
// We use protobuf-javalite for actual serialization.

// Since we can't run protoc in GitHub Actions easily, we use
// a JSON-over-zlib protocol instead of protobuf for the Android client.
// The Python server already supports both — we just send JSON frames
// with the same structure.

// Actually let's use a simpler approach: reuse the existing JSON protocol
// from the Python codebase but call it from Kotlin.

// Message type constants matching Python's protobuf field names
object MsgType {
    const val HELLO         = "hello"
    const val PING          = "ping"
    const val PONG          = "pong"
    const val INFO_REQUEST  = "info_request"
    const val INFO_RESPONSE = "info_response"
    const val EXEC_REQUEST  = "exec_request"
    const val EXEC_RESPONSE = "exec_response"
    const val FILE_HEADER   = "file_header"
    const val FILE_ACK      = "file_ack"
    const val SCREENSHOT_REQ  = "screenshot_req"
    const val SCREENSHOT_DATA = "screenshot_data"
    const val ECHO_REQUEST  = "echo_request"
    const val ECHO_RESPONSE = "echo_response"
    const val ERROR_MSG     = "error_msg"
    const val BYE           = "bye"
}
