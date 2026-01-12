package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets

class PingBlenderAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val port = BlenderProbeManager.activePort
        if (port == null) {
            Messages.showErrorDialog("Blender is not running or Probe server is not ready.", "Connection Error")
            return
        }

        try {
            Socket("127.0.0.1", port).use { socket ->
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)

                val json = """{"action": "ping"}"""
                val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)

                val header = String.format("%-64s", jsonBytes.size.toString())

                writer.write(header)
                writer.write(json)
                writer.flush()
            }
            Messages.showInfoMessage("Ping sent to Blender!", "Blender Probe")
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                "Could not connect to Blender. Is it running via Blender Probe?\n${ex.message}",
                "Connection Error"
            )
        }
    }
}