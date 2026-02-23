package com.github.unclepomedev.blenderprobeforpycharm


import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Utility functions for handling script resources.
 * Provides methods to extract Python scripts from the plugin resources to the file system.
 */
object ScriptResourceUtils {
    /**
     * Extracts a script from the plugin resources to a temporary directory.
     * The script is expected to be located in the `/python/` directory of the resources.
     *
     * @param scriptName The name of the script file to extract.
     * @return The extracted script file.
     * @throws IllegalStateException if the script resource is not found.
     */
    fun extractScriptToTemp(scriptName: String): File {
        val resourcePath = "/python/$scriptName"
        val inputStream = this::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Script not found: $resourcePath")

        val tempDir = FileUtil.createTempDirectory("blender_stubs", null)
        val scriptFile = File(tempDir, scriptName)

        Files.copy(inputStream, scriptFile.toPath())

        return scriptFile
    }

    /**
     * Extracts a resource script to a temporary file with a specified prefix.
     *
     * @param resourcePath The path to the resource script.
     * @param tempFileNamePrefix The prefix for the temporary file name.
     * @return The extracted temporary file.
     * @throws IOException if the resource is not found or an error occurs during extraction.
     */
    fun extractResourceScript(resourcePath: String, tempFileNamePrefix: String): File {
        val path = if (resourcePath.startsWith("/")) resourcePath else "/$resourcePath"

        val stream = javaClass.getResourceAsStream(path)
            ?: throw IOException("Resource not found: $path")

        val tempFile = FileUtil.createTempFile(tempFileNamePrefix, ".py", true)

        stream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }
}