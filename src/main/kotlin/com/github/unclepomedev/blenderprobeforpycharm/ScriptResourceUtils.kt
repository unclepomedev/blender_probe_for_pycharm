package com.github.unclepomedev.blenderprobeforpycharm


import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files

object ScriptResourceUtils {
    fun extractScriptToTemp(scriptName: String): File {
        val resourcePath = "/python/$scriptName"
        val inputStream = this::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Script not found: $resourcePath")

        val tempDir = FileUtil.createTempDirectory("blender_stubs", null)
        val scriptFile = File(tempDir, scriptName)

        Files.copy(inputStream, scriptFile.toPath())

        return scriptFile
    }

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