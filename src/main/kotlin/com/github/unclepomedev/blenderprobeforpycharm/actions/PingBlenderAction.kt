package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.probe.BlenderProbeClient
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages

/**
 * Action to send a ping command to the running Blender instance. This is used to verify
 * connectivity with the Blender Probe server.
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
            Messages.showErrorDialog(
                "Blender is not running or Probe server is not ready.",
                "Connection Error",
            )
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val json = """{"action": "ping"}"""
                BlenderProbeClient.sendCommand(port, json)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage("Ping sent to Blender!", "Blender Probe")
                }
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        "Could not connect to Blender. Is it running via Blender Probe?\n${ex.message}",
                        "Connection Error",
                    )
                }
            }
        }
    }
}
