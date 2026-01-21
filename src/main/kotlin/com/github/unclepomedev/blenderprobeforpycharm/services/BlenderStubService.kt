package com.github.unclepomedev.blenderprobeforpycharm.services

import com.github.unclepomedev.blenderprobeforpycharm.ScriptResourceUtils
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable

@Service(Service.Level.PROJECT)
class BlenderStubService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(BlenderStubService::class.java)
        private const val PROCESS_TIMEOUT_MS = 5 * 60 * 1000L
        fun getInstance(project: Project): BlenderStubService = project.service()
    }

    fun generateStubs(blenderPath: String) {
        val basePath = project.basePath ?: return
        val outputDir = File(basePath, ".blender_stubs")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Blender stubs...", true) {
            private var virtualOutputDir: VirtualFile? = null
            private var executionLog: String = ""

            override fun run(indicator: ProgressIndicator) {
                try {
                    executionLog = runBlenderProcess(blenderPath, outputDir, indicator)

                    LOG.info("Blender stub generation finished. Log:\n$executionLog")

                    indicator.text = "Refreshing file system..."
                    virtualOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputDir)
                    virtualOutputDir?.refresh(false, true)

                } catch (_: ProcessCanceledException) {
                    LOG.info("Stub generation cancelled by user.")
                    notifyUser(
                        "Generation Cancelled",
                        "Blender stub generation was cancelled.",
                        NotificationType.INFORMATION
                    )
                } catch (ex: Exception) {
                    LOG.warn("Blender Probe Failed to generate stubs: ${ex.message}")
                    notifyUser(
                        "Generation Failed",
                        "Failed to generate Blender stubs.\nCheck the log for details.",
                        NotificationType.ERROR
                    )
                }
            }

            override fun onSuccess() {
                val dir = virtualOutputDir ?: return

                if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
                    notifyUser("Success", "Stubs generated in .blender_stubs", NotificationType.INFORMATION)
                }
                markDirectoryAsSourceRoot(dir)
            }
        })
    }

    private fun runBlenderProcess(blenderPath: String, outputDir: File, indicator: ProgressIndicator): String {
        val blenderExe = File(blenderPath)
        if (!blenderExe.exists()) {
            throw ExecutionException("Blender executable not found at: $blenderPath")
        }

        indicator.text = "Extracting generator script..."
        val scriptFile = ScriptResourceUtils.extractScriptToTemp("generate_stubs.py")

        indicator.text = "Running blender..."
        val commandLine = GeneralCommandLine(
            blenderPath,
            "--factory-startup",
            "-b",
            "-P", scriptFile.absolutePath,
            "--",
            "--output", outputDir.absolutePath
        ).apply {
            charset = StandardCharsets.UTF_8
        }

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

        val startTime = System.currentTimeMillis()
        var finished = false
        while (!finished) {
            if (indicator.isCanceled) {
                handler.destroyProcess()
                throw ProcessCanceledException()
            }

            if (System.currentTimeMillis() - startTime > PROCESS_TIMEOUT_MS) {
                handler.destroyProcess()
                throw RuntimeException("Blender process timed out after ${PROCESS_TIMEOUT_MS / 1000} seconds")
            }

            finished = handler.waitFor(500) // 0.5秒待機
        }

        if (handler.exitCode != 0) {
            throw RuntimeException("Blender exited with code ${handler.exitCode}.\nOutput:\n$outputBuilder")
        }
        return outputBuilder.toString()
    }

    private fun notifyUser(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Blender Probe Notification Group")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun markDirectoryAsSourceRoot(dir: VirtualFile) {
        val basePath = project.basePath ?: return
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return

        ReadAction.nonBlocking(Callable<Module?> {
            if (project.isDisposed) return@Callable null
            ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(baseDir)
        })
            .expireWith(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { module ->
                if (module != null && !module.isDisposed) {
                    ApplicationManager.getApplication().runWriteAction {
                        ModuleRootModificationUtil.updateModel(module) { model ->
                            val contentEntry = model.contentEntries.find { entry ->
                                entry.file?.let { VfsUtil.isAncestor(it, dir, false) } == true
                            } ?: return@updateModel

                            if (contentEntry.sourceFolders.none { it.url == dir.url }) {
                                contentEntry.addSourceFolder(dir, false)
                            }
                        }
                    }
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}