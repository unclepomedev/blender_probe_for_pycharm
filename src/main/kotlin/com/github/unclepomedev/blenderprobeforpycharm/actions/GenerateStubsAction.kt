package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderStubService
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages

class GenerateStubsAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(GenerateStubsAction::class.java)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = BlenderSettings.getInstance(project)

        var blenderPath = settings.resolveBlenderPath()

        if (blenderPath.isNullOrBlank()) {
            if (ApplicationManager.getApplication().isHeadlessEnvironment) {
                LOG.warn("Blender path not found, skipping stub generation.")
                return
            }

            val result = Messages.showOkCancelDialog(
                project,
                "Blender executable could not be found.\nPlease configure the path manually or ensure 'blup' is installed.",
                "Configuration Required",
                "Open Settings",
                "Cancel",
                Messages.getWarningIcon()
            )

            if (result == Messages.OK) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Blender Probe")
                blenderPath = settings.resolveBlenderPath()
            }

            if (blenderPath.isNullOrBlank()) return
        }

        BlenderStubService.getInstance(project).generateStubs(blenderPath)
    }
}