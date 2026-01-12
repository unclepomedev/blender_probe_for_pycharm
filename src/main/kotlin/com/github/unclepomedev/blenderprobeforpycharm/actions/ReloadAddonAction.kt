package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets

class ReloadAddonAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val port = BlenderProbeManager.activePort

        if (port == null) {
            Messages.showErrorDialog("Blender is not connected.", "Reload Failed")
            return
        }

        val addonName = BlenderProbeUtils.findAddonPackageName(project)
        if (addonName == null) {
            Messages.showErrorDialog("Could not auto-detect addon package (folder with __init__.py).", "Reload Failed")
            return
        }

        try {
            Socket("127.0.0.1", port).use { socket ->
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)

                val safeName = addonName.replace("\\", "\\\\").replace("\"", "\\\"")
                val json = """{"action": "reload", "module_name": "$safeName"}"""
                val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)
                val header = String.format("%-64s", jsonBytes.size.toString())

                writer.write(header)
                writer.write(json)
                writer.flush()
            }
            println("Sent reload command for $addonName")
        } catch (ex: Exception) {
            Messages.showErrorDialog("Failed to send reload command.\n${ex.message}", "Connection Error")
        }
    }
}