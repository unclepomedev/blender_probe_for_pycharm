package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import org.junit.Assume

/** Shared helpers for smoke tests that launch a real Blender process via blup. */
abstract class BaseSmokeTest : BaseBlenderTest() {

    /** Resolves the Blender binary via blup, skipping the test if none is installed. */
    protected fun requireBlenderBinary(): String {
        val path = BlenderSettings.getInstance(project).resolveBlenderPath()
        Assume.assumeTrue("No Blender resolved via blup; skipping.", path != null)
        return path!!
    }

    /** Sends a probe command using the same wire format as PingBlenderAction/ReloadAddonAction. */
    protected fun sendProbeCommand(port: Int, json: String) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 3_000)
            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            val body = json.toByteArray(StandardCharsets.UTF_8)
            writer.write(String.format("%-64s", body.size.toString()))
            writer.write(json)
            writer.flush()
        }
    }
}
