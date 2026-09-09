package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.manifest.AddonDetectionResult
import com.github.unclepomedev.blenderprobeforpycharm.manifest.BlenderManifestDetector
import com.github.unclepomedev.blenderprobeforpycharm.probe.BlenderProbeClient
import com.github.unclepomedev.blenderprobeforpycharm.ui.BlenderNotificationUtils
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * Action to reload the Blender add-on. This sends a reload command to the running Blender instance
 * via the probe server.
 */
class ReloadAddonAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val port = BlenderProbeManager.activePort

        if (port == null) {
            notifyConnectionError(project, "Blender Probe is not connected.")
            return
        }

        executeReloadTask(project, port)
    }

    private fun executeReloadTask(project: Project, port: Int) {
        ProgressManager.getInstance()
            .run(
                object : Task.Backgroundable(project, "Reloading blender addon", false) {
                    override fun run(indicator: ProgressIndicator) {
                        val detection =
                            ApplicationManager.getApplication().runReadAction<
                                AddonDetectionResult
                            > {
                                BlenderManifestDetector.detectAddon(project)
                            }
                        if (!detection.isResolved) {
                            ApplicationManager.getApplication().invokeLater {
                                notifyDetectionFailed(project, detection)
                            }
                            return
                        }
                        performReload(project, port, detection)
                    }
                }
            )
    }

    private fun performReload(project: Project, port: Int, detection: AddonDetectionResult) {
        val addonName = detection.moduleName
        try {
            BlenderProbeClient.sendReloadCommand(port, addonName)
            notifyReloadSuccess(project, addonName, detection)
        } catch (ex: Exception) {
            notifyReloadFailure(project, ex)
        }
    }

    private fun notifyConnectionError(project: Project, message: String) {
        BlenderNotificationUtils.showNotificationWithSettings(
            project,
            "Connection Error",
            message,
            NotificationType.ERROR,
        )
    }

    private fun notifyDetectionFailed(project: Project, detection: AddonDetectionResult) {
        BlenderNotificationUtils.showNotificationWithSettings(
            project,
            "Addon Detection Failed",
            detection.formatMessage(),
            NotificationType.ERROR,
        )
    }

    private fun notifyReloadSuccess(
        project: Project,
        addonName: String,
        detection: AddonDetectionResult,
    ) {
        val successContent = buildString {
            append("Reload command sent for '$addonName'.\n")
            append(detection.formatMessage())
        }
        BlenderNotificationUtils.showNotificationWithSettings(
            project,
            "Addon Reloaded",
            successContent,
            NotificationType.INFORMATION,
        )
    }

    private fun notifyReloadFailure(project: Project, ex: Exception) {
        ApplicationManager.getApplication().invokeLater {
            notifyConnectionError(project, "Failed to send reload command: ${ex.message}")
        }
    }
}
