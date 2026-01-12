package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.BufferedOutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

class ReloadAddonAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val port = BlenderProbeManager.activePort

        if (port == null) {
            Messages.showErrorDialog(project, "Blender Probe is not connected.", "Connection Error")
            return
        }

        val detectedName = BlenderProbeUtils.findAddonPackageName(project)
        val addonName = detectedName ?: project.name.lowercase().replace(" ", "_").replace("-", "_")

        try {
            Socket("127.0.0.1", port).use { socket ->
                val out = BufferedOutputStream(socket.getOutputStream())

                val safeName = addonName.replace("\\", "\\\\").replace("\"", "\\\"")
                val json = """{"action": "reload", "module_name": "$safeName"}"""
                val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)
                val header = String.format("%-64s", jsonBytes.size.toString())
                val headerBytes = header.toByteArray(StandardCharsets.UTF_8)

                out.write(headerBytes)
                out.write(jsonBytes)
                out.flush()
            }
        } catch (ex: Exception) {
            Messages.showErrorDialog(project, "Failed to send command: ${ex.message}", "Connection Error")
        }
    }
}