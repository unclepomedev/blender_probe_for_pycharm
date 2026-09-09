package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Service responsible for generating Python stubs for the Blender API. It runs a Blender process in
 * the background to extract API information and generate stub files.
 */
@Service(Service.Level.PROJECT)
class BlenderStubService(private val project: Project) {

    private val notifier = BlenderStubNotifier(project)

    companion object {
        /**
         * Retrieves the instance of BlenderStubService for the given project.
         *
         * @param project The project to get the service for.
         * @return The BlenderStubService instance.
         */
        fun getInstance(project: Project): BlenderStubService = project.service()
    }

    /**
     * Generates Blender API stubs using the specified Blender executable. The process runs
     * asynchronously with a progress indicator.
     *
     * @param blenderPath The path to the Blender executable.
     */
    fun generateStubs(blenderPath: String) {
        val basePath = project.basePath ?: return
        val outputDir = File(basePath, ".blender_stubs")

        val task = BlenderStubGenerationTask(project, blenderPath, outputDir, notifier)
        ProgressManager.getInstance().run(task)
    }

    /** Prepares the Python generator environment by extracting files from resources. */
    fun prepareGeneratorEnvironment(tempDir: File, indicator: ProgressIndicator): String {
        return BlenderStubEnvironmentPreparer.prepare(tempDir, indicator)
    }

    /** Executes the Blender process with the stub generator script. */
    fun runBlenderProcess(
        blenderPath: String,
        scriptPath: String,
        outputDir: File,
        indicator: ProgressIndicator,
    ): String {
        return BlenderStubProcessRunner.run(blenderPath, scriptPath, outputDir, indicator)
    }
}
