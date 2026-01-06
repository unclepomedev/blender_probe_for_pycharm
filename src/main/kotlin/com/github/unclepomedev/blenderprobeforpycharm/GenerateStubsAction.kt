package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import java.io.File

class GenerateStubsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val blenderPath = Messages.showInputDialog(
            project,
            "Blender executable path:",
            "Blender Path",
            Messages.getQuestionIcon()
        )
        if (blenderPath.isNullOrBlank()) return

        val basePath = project.basePath ?: return
        val outputDir = File(basePath, "typings")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating blender stubs...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    generateStubs(blenderPath, outputDir, indicator)
                } catch (ex: Exception) {
                    throw ex
                }
            }

            override fun onSuccess() {
                Messages.showInfoMessage(project, "Stubs generated in ${outputDir.absolutePath}", "Success")
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputDir)?.refresh(false, true)
            }
        })
    }

    private fun generateStubs(blenderPath: String, outputDir: File, indicator: ProgressIndicator) {
        indicator.text = "Extracting script..."
        val scriptFile = ScriptResourceUtils.extractScriptToTemp("generate_stubs.py")

        indicator.text = "Running blender..."

        val commandLine = GeneralCommandLine(
            blenderPath,
            "--factory-startup",
            "-b",
            "-P", scriptFile.absolutePath,
            "--",
            "--output", outputDir.absolutePath
        )

        val handler = OSProcessHandler(commandLine)

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {}
            override fun startNotified(event: ProcessEvent) {}
            override fun processTerminated(event: ProcessEvent) {}
        })

        handler.startNotify()
        handler.waitFor()

        if (handler.exitCode != 0) {
            throw RuntimeException("Blender exited with code ${handler.exitCode}")
        }
    }
}