package com.github.unclepomedev.blenderprobeforpycharm.probe

import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlenderProbeClientTest {

    @Test
    fun testCreateReloadCommandJson() {
        val json = BlenderProbeClient.createReloadCommandJson("my_addon")
        assertEquals("""{"action": "reload", "module_name": "my_addon"}""", json)
    }

    @Test
    fun testCreateReloadCommandJsonEscapesSpecialCharacters() {
        val json = BlenderProbeClient.createReloadCommandJson("my\\addon\"test")
        assertEquals("""{"action": "reload", "module_name": "my\\addon\"test"}""", json)
    }

    @Test
    fun testFormatMessage() {
        val json = """{"action": "ping"}"""
        val payload = BlenderProbeClient.formatMessage(json)

        val headerBytes = payload.copyOfRange(0, 64)
        val bodyBytes = payload.copyOfRange(64, payload.size)

        val headerStr = String(headerBytes, StandardCharsets.UTF_8)
        val bodyStr = String(bodyBytes, StandardCharsets.UTF_8)

        assertEquals(64, headerBytes.size)
        assertEquals(bodyBytes.size, headerStr.trim().toInt())
        assertEquals(json, bodyStr)
    }

    @Test
    fun testSendReloadCommand() {
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        try {
            val future =
                executor.submit<Pair<String, String>> {
                    server.accept().use { socket ->
                        val input = socket.getInputStream()
                        val headerBuf = ByteArray(64)
                        var read = 0
                        while (read < 64) {
                            val count = input.read(headerBuf, read, 64 - read)
                            if (count < 0) break
                            read += count
                        }
                        val header = String(headerBuf, StandardCharsets.UTF_8)
                        val length = header.trim().toInt()
                        val bodyBuf = ByteArray(length)
                        read = 0
                        while (read < length) {
                            val count = input.read(bodyBuf, read, length - read)
                            if (count < 0) break
                            read += count
                        }
                        val body = String(bodyBuf, StandardCharsets.UTF_8)
                        Pair(header, body)
                    }
                }

            BlenderProbeClient.sendReloadCommand(port, "test_addon", timeoutMs = 1_000)

            val (header, body) = future.get(3, TimeUnit.SECONDS)
            assertTrue(header.length == 64)
            val expectedJson = """{"action": "reload", "module_name": "test_addon"}"""
            assertEquals(
                expectedJson.toByteArray(StandardCharsets.UTF_8).size,
                header.trim().toInt(),
            )
            assertEquals(expectedJson, body)
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }
}
