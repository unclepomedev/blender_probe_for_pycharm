package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Handles extracting generator scripts and templates from plugin resources into a temporary
 * directory for execution.
 */
internal object BlenderStubEnvironmentPreparer {

    private const val MANIFEST_PATH = "python/file_list.txt"
    private const val MAIN_SCRIPT_NAME = "generate_stubs.py"

    /**
     * Prepares the Python generator environment by extracting files listed in the manifest.
     *
     * @param tempDir The target directory to extract the generator scripts to.
     * @param indicator Progress indicator to update progress messages.
     * @return Absolute path to the main generator script.
     * @throws ExecutionException If the manifest or scripts cannot be found/extracted.
     */
    fun prepare(tempDir: File, indicator: ProgressIndicator): String {
        indicator.text = "Preparing generator scripts..."

        val filesToCopy = readManifest()
        for (relativePath in filesToCopy) {
            val resourcePath = "python/$relativePath"
            val destFile = File(tempDir, relativePath)
            extractResource(resourcePath, destFile)
        }

        val mainScript = File(tempDir, MAIN_SCRIPT_NAME)
        if (!mainScript.exists()) {
            throw ExecutionException(
                "Main script '$MAIN_SCRIPT_NAME' was not found in the extracted files."
            )
        }
        return mainScript.absolutePath
    }

    private fun readManifest(): List<String> {
        val manifestStream =
            this::class.java.classLoader.getResourceAsStream(MANIFEST_PATH)
                ?: throw ExecutionException("Manifest file not found in resources: $MANIFEST_PATH")

        val filesToCopy =
            manifestStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                reader
                    .readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
            }

        if (filesToCopy.isEmpty()) {
            throw ExecutionException("Manifest file list is empty.")
        }
        return filesToCopy
    }

    private fun extractResource(resourcePath: String, destFile: File) {
        val resourceStream =
            this::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: throw ExecutionException("Resource not found: $resourcePath")

        destFile.parentFile?.mkdirs()

        resourceStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
