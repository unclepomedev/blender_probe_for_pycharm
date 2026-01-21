package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
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
import com.intellij.openapi.util.io.FileUtil
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
                var tempDir: File? = null
                try {
                    tempDir = FileUtil.createTempDirectory("blender_probe_gen", null)
                    val scriptPath = prepareGeneratorEnvironment(tempDir, indicator)

                    executionLog = runBlenderProcess(blenderPath, scriptPath, outputDir, indicator)

                    LOG.info("Blender stub generation finished.")
                    indicator.text = "Refreshing file system..."
                    virtualOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputDir)
                    virtualOutputDir?.refresh(false, true)

                } catch (_: ProcessCanceledException) {
                    LOG.info("Stub generation cancelled.")
                    notifyUser(
                        "Generation Cancelled",
                        "Blender stub generation was cancelled.",
                        NotificationType.INFORMATION
                    )
                } catch (ex: Exception) {
                    LOG.warn("Blender Probe Failed: ${ex.message}")
                    notifyUser("Generation Failed", "Failed to generate stubs: ${ex.message}", NotificationType.ERROR)
                } finally {
                    tempDir?.let { FileUtil.delete(it) }
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

    private fun prepareGeneratorEnvironment(tempDir: File, indicator: ProgressIndicator): String {
        indicator.text = "Preparing generator scripts..."

        val manifestPath = "python/file_list.txt"
        val manifestStream = this::class.java.classLoader.getResourceAsStream(manifestPath)
            ?: throw ExecutionException("Manifest file not found in resources: $manifestPath")

        val filesToCopy = manifestStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        }

        if (filesToCopy.isEmpty()) {
            throw ExecutionException("Manifest file list is empty.")
        }

        for (relativePath in filesToCopy) {
            val resourcePath = "python/$relativePath"
            val destFile = File(tempDir, relativePath)

            extractResource(resourcePath, destFile)
        }

        val mainScript = File(tempDir, "generate_stubs.py")
        if (!mainScript.exists()) {
            throw ExecutionException("Main script 'generate_stubs.py' was not found in the extracted files.")
        }
        return mainScript.absolutePath
    }

    private fun extractResource(resourcePath: String, destFile: File) {
        val resourceStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: throw ExecutionException("Resource not found: $resourcePath")

        destFile.parentFile?.mkdirs()

        resourceStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun runBlenderProcess(
        blenderPath: String,
        scriptPath: String,
        outputDir: File,
        indicator: ProgressIndicator
    ): String {
        val blenderExe = File(blenderPath)
        if (!blenderExe.exists() || !blenderExe.isFile || !blenderExe.canExecute()) {
            throw ExecutionException("Blender executable not found or not executable at: $blenderPath")
        }

        indicator.text = "Running blender..."
        val commandLine = GeneralCommandLine(
            blenderPath,
            "--factory-startup",
            "-b",
            "-P", scriptPath,
            "--",
            "--output", outputDir.absolutePath
        ).apply {
            charset = StandardCharsets.UTF_8
            environment["PYTHONUNBUFFERED"] = "1"
        }

        val handler = com.intellij.execution.process.CapturingProcessHandler(commandLine)

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = event.text

                print("[Blender Stream] $text")

                if (outputType != ProcessOutputTypes.STDERR) {
                    val cleanText = text.trim()
                    if (cleanText.isNotEmpty()) {
                        indicator.text2 = cleanText
                    }
                }
            }
            override fun startNotified(event: ProcessEvent) {}
            override fun processTerminated(event: ProcessEvent) {}
        })

        val output = handler.runProcessWithProgressIndicator(indicator, PROCESS_TIMEOUT_MS.toInt())

        if (indicator.isCanceled) {
            throw ProcessCanceledException()
        }

        if (output.isTimeout) {
            throw RuntimeException("Blender process timed out.")
        }

        if (output.exitCode != 0) {
            val errorMsg = output.stderr.ifBlank { output.stdout }
            throw RuntimeException(
                "Blender exited with code ${output.exitCode}.\nError Details:\n$errorMsg"
            )
        }
        return output.stdout
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