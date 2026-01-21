package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderStubService
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class GenerateStubsAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(GenerateStubsAction::class.java)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        resolveAndGenerate(project)
    }

    private fun resolveAndGenerate(project: Project) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Blender probe: initializing", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Resolving Blender executable..."

                val settings = BlenderSettings.getInstance(project)

                val blenderPath = settings.resolveBlenderPath()

                if (blenderPath.isNullOrBlank()) {
                    ApplicationManager.getApplication().invokeLater {
                        handleMissingPath(project)
                    }
                } else {
                    indicator.text = "Generating stubs..."
                    BlenderStubService.getInstance(project).generateStubs(blenderPath)
                }
            }
        })
    }

    private fun handleMissingPath(project: Project) {
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

            resolveAndGenerate(project)
        }
    }
}