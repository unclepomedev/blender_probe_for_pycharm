package com.github.unclepomedev.blenderprobeforpycharm.probe

import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/** Client for communicating with the running Blender Probe socket server. */
object BlenderProbeClient {
    private const val DEFAULT_HOST = "127.0.0.1"
    private const val HEADER_LENGTH = 64
    private const val DEFAULT_TIMEOUT_MS = 3_000

    /** Formats a raw JSON payload with a fixed 64-byte padded length header. */
    fun formatMessage(json: String): ByteArray {
        val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)
        val header = String.format("%-${HEADER_LENGTH}s", jsonBytes.size.toString())
        val headerBytes = header.toByteArray(StandardCharsets.UTF_8)
        return headerBytes + jsonBytes
    }

    /** Escapes a string to be safely embedded inside a JSON string literal. */
    fun escapeJsonString(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001f' -> append(String.format("\\u%04x", char.code))
                else -> append(char)
            }
        }
    }

    /** Creates the JSON command string to reload the specified add-on module. */
    fun createReloadCommandJson(moduleName: String): String {
        val safeName = escapeJsonString(moduleName)
        return """{"action": "reload", "module_name": "$safeName"}"""
    }

    /**
     * Sends a raw JSON command string to the Blender Probe server running on [port].
     *
     * @param port The port the probe server is listening on.
     * @param json The JSON string to send.
     * @param timeoutMs Socket connection timeout in milliseconds.
     */
    fun sendCommand(port: Int, json: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS) {
        val payload = formatMessage(json)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(DEFAULT_HOST, port), timeoutMs)
            val out = BufferedOutputStream(socket.getOutputStream())
            out.write(payload)
            out.flush()
        }
    }

    /** Sends a reload command for [moduleName] to the Blender Probe server on [port]. */
    fun sendReloadCommand(port: Int, moduleName: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS) {
        sendCommand(port, createReloadCommandJson(moduleName), timeoutMs)
    }
}
