package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Background task responsible for orchestrating the generation of Blender stubs: preparing the
 * Python environment, executing Blender, refreshing the output directory, and registering the
 * output as a source root.
 */
internal class BlenderStubGenerationTask(
    project: Project,
    private val blenderPath: String,
    private val outputDir: File,
    private val notifier: BlenderStubNotifier,
) : Task.Backgroundable(project, "Generating Blender stubs...", true) {

    private var virtualOutputDir: VirtualFile? = null
    private var executionLog: String = ""

    override fun run(indicator: ProgressIndicator) {
        var tempDir: File? = null
        try {
            tempDir = FileUtil.createTempDirectory("blender_probe_gen", null)
            val scriptPath = BlenderStubEnvironmentPreparer.prepare(tempDir, indicator)

            executionLog =
                BlenderStubProcessRunner.run(blenderPath, scriptPath, outputDir, indicator)

            LOG.info("Blender stub generation finished.")
            indicator.text = "Refreshing file system..."
            virtualOutputDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputDir)
            virtualOutputDir?.refresh(false, true)
        } catch (e: ProcessCanceledException) {
            LOG.info("Stub generation cancelled.")
            notifier.notifyCancelled()
            throw e
        } catch (ex: Exception) {
            LOG.warn("Blender Probe Failed: ${ex.message}")
            notifier.notifyFailed(ex.message ?: "Unknown error")
        } finally {
            tempDir?.let { FileUtil.delete(it) }
        }
    }

    override fun onSuccess() {
        val dir = virtualOutputDir ?: return
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
            notifier.notifySuccess()
        }
        BlenderStubSourceRootManager.markAsSourceRoot(project, dir)
    }

    companion object {
        private val LOG = Logger.getInstance(BlenderStubGenerationTask::class.java)
    }
}
