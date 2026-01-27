package com.github.unclepomedev.blenderprobeforpycharm.run.common

import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.runners.ExecutionEnvironment
import java.nio.charset.StandardCharsets

abstract class AbstractBlenderRunningState(environment: ExecutionEnvironment) : CommandLineState(environment) {

    var cachedBlenderPath: String? = null
    var cachedAddonName: String? = null
    var cachedSourceRoot: String? = null

    protected fun createBaseCommandLine(): GeneralCommandLine {
        val project = environment.project
        val blenderPath = cachedBlenderPath ?: BlenderSettings.getInstance(project).resolveBlenderPath()

        if (blenderPath.isNullOrEmpty()) {
            throw ExecutionException("Blender executable not found. Please configure it in Settings or install 'blup'.")
        }

        val cmd = GeneralCommandLine()
            .withExePath(blenderPath)
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment("PYTHONDONTWRITEBYTECODE", "1")
            .withEnvironment("PYTHONUNBUFFERED", "1")

        cachedSourceRoot?.let {
            cmd.withEnvironment("BLENDER_PROBE_PROJECT_ROOT", it)
        }
        cachedAddonName?.let {
            cmd.withEnvironment("BLENDER_PROBE_ADDON_NAME", it)
        }

        project.basePath?.let {
            cmd.withWorkDirectory(it)
        }

        return cmd
    }
}