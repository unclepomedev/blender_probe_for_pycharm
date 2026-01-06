package com.github.unclepomedev.blenderprobeforpycharm

import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.util.concurrent.Callable

class GenerateStubsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val settings = BlenderSettings.getInstance(project)
        val blenderPath = settings.state.blenderPath
        if (blenderPath.isBlank()) {
            val result = Messages.showOkCancelDialog(
                project,
                "Blender path is not configured. Please set the path in Settings.",
                "Configuration Required",
                "Open Settings",
                "Cancel",
                Messages.getWarningIcon()
            )
            if (result == Messages.OK) {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, "Blender Probe")
            }
            return
        }

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
                val virtualOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputDir)
                virtualOutputDir?.refresh(false, true)
                if (virtualOutputDir != null) {
                    markDirectoryAsSourceRoot(project, virtualOutputDir)
                }
            }
        })
    }

    private fun markDirectoryAsSourceRoot(project: Project, dir: VirtualFile) {
        val basePath = project.basePath ?: return
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return

        ReadAction.nonBlocking(Callable<com.intellij.openapi.module.Module?> {
            if (project.isDisposed) return@Callable null
            ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(baseDir)
        })
            .expireWith(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { module ->
                if (module != null && !module.isDisposed) {
                    runWriteActionSafe(module, dir)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun runWriteActionSafe(module: com.intellij.openapi.module.Module, dir: VirtualFile) {
        ApplicationManager.getApplication().runWriteAction {
            ModuleRootModificationUtil.updateModel(module) { model ->
                val contentEntry = model.contentEntries.find { entry ->
                    val entryFile = entry.file
                    entryFile != null && VfsUtil.isAncestor(entryFile, dir, false)
                } ?: return@updateModel

                val alreadyRegistered = contentEntry.sourceFolders.any { it.url == dir.url }
                if (!alreadyRegistered) {
                    contentEntry.addSourceFolder(dir, false)
                }
            }
        }
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