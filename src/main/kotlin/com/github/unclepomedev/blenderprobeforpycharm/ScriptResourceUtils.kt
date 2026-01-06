package com.github.unclepomedev.blenderprobeforpycharm


import com.intellij.openapi.util.io.FileUtil
import java.io.File
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
}