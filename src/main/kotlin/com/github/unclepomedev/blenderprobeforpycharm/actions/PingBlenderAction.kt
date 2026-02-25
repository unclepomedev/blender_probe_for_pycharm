package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Action to send a ping command to the running Blender instance.
 * This is used to verify connectivity with the Blender Probe server.
 */
class PingBlenderAction : AnAction() {
    /**
     * Executes the ping action.
     *
     * @param e The action event.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val port = BlenderProbeManager.activePort
        if (port == null) {
            Messages.showErrorDialog("Blender is not running or Probe server is not ready.", "Connection Error")
            return
        }

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 3_000)
                socket.soTimeout = 3_000
                socket.use {
                    val writer = OutputStreamWriter(it.getOutputStream(), StandardCharsets.UTF_8)

                    val json = """{"action": "ping"}"""
                    val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)

                    val header = String.format("%-64s", jsonBytes.size.toString())

                    writer.write(header)
                    writer.write(json)
                    writer.flush()
                }
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage("Ping sent to Blender!", "Blender Probe")
                }
            } catch (ex: Exception) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        "Could not connect to Blender. Is it running via Blender Probe?\n${ex.message}",
                        "Connection Error"
                    )
                }
            }
        }
    }
}