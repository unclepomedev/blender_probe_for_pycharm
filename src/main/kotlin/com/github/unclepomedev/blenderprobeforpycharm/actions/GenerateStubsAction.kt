package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.ScriptResourceUtils
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ShowSettingsUtil
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
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable

class GenerateStubsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        regenerateStubs(project)
    }

    companion object {
        private val LOG = Logger.getInstance(GenerateStubsAction::class.java)

        fun regenerateStubs(project: Project) {
            val settings = BlenderSettings.getInstance(project)
            var blenderPath = settings.state.blenderPath

            if (blenderPath.isBlank()) {
                if (ApplicationManager.getApplication().isHeadlessEnvironment) {
                    LOG.warn("Blender path is missing, skipping stub generation in headless mode.")
                    return
                }

                val result = Messages.showOkCancelDialog(
                    project,
                    "Blender path is not configured. Please set the path in Settings.",
                    "Configuration Required",
                    "Open Settings",
                    "Cancel",
                    Messages.getWarningIcon()
                )

                if (result == Messages.OK) {
                    try {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Blender Probe")
                    } catch (e: Exception) {
                        LOG.warn("Settings dialog closed with exception (ignoring): ${e.message}")
                    }
                    blenderPath = settings.state.blenderPath
                } else {
                    return
                }
            }

            if (blenderPath.isBlank()) {
                return
            }

            val basePath = project.basePath ?: return
            val outputDir = File(basePath, ".blender_stubs")

            ProgressManager.getInstance()
                .run(object : Task.Backgroundable(project, "Generating blender stubs...", true) {
                    private var virtualOutputDir: VirtualFile? = null
                    private var executionLog: String = ""

                    override fun run(indicator: ProgressIndicator) {
                        try {
                            executionLog = generateStubsProcess(blenderPath, outputDir, indicator)

                            println("=== Blender Probe Execution Log ===")
                            println(executionLog)
                            println("==================================")

                            indicator.text = "Refreshing file system..."
                            virtualOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputDir)
                            virtualOutputDir?.refresh(false, true)

                        } catch (ex: Exception) {
                            println("=== Blender Probe FAILED ===")
                            println(executionLog)
                            LOG.warn("Blender Probe Failed to generate stubs. Error: ${ex.message}")
                        }
                    }

                    override fun onSuccess() {
                        if (virtualOutputDir != null) {
                            LOG.info("Blender Probe Finished Successfully.")

                            if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
                                Messages.showInfoMessage(
                                    project,
                                    "Stubs generated in ${outputDir.absolutePath}",
                                    "Success"
                                )
                            }

                            virtualOutputDir?.let { dir ->
                                markDirectoryAsSourceRoot(project, dir)
                            }
                        }
                    }
                })
        }

        private fun generateStubsProcess(blenderPath: String, outputDir: File, indicator: ProgressIndicator): String {
            val blenderExe = File(blenderPath)
            if (!blenderExe.exists() || !blenderExe.isFile) {
                throw ExecutionException("Blender executable not found at: $blenderPath")
            }

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
            commandLine.charset = StandardCharsets.UTF_8

            val handler = OSProcessHandler(commandLine)
            val outputBuilder = StringBuilder()

            handler.addProcessListener(object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val text = event.text
                    outputBuilder.append(text)
                    val cleanText = text.trim()
                    if (cleanText.isNotEmpty()) {
                        indicator.text2 = cleanText
                    }
                }

                override fun startNotified(event: ProcessEvent) {}
                override fun processTerminated(event: ProcessEvent) {}
            })

            handler.startNotify()
            handler.waitFor()

            if (handler.exitCode != 0) {
                throw RuntimeException("Blender exited with code ${handler.exitCode}.\nOutput:\n$outputBuilder")
            }
            return outputBuilder.toString()
        }

        private fun markDirectoryAsSourceRoot(project: Project, dir: VirtualFile) {
            val basePath = project.basePath ?: return
            val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return

            ReadAction.nonBlocking(Callable<Module?> {
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

        private fun runWriteActionSafe(module: Module, dir: VirtualFile) {
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
    }
}