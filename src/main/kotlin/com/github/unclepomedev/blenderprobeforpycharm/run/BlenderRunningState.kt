package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.github.unclepomedev.blenderprobeforpycharm.ScriptResourceUtils
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import java.nio.charset.StandardCharsets

class BlenderRunningState(
    environment: ExecutionEnvironment,
) : CommandLineState(environment) {

    var debugPort: Int? = null
    var pydevdPath: String? = null

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor)
        console?.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler)
    }

    override fun startProcess(): ProcessHandler {
        val project = environment.project
        val settings = BlenderSettings.getInstance(project)
        val blenderPath = settings.resolveBlenderPath()

        if (blenderPath.isNullOrEmpty()) {
            throw ExecutionException("Blender executable not found. Please configure it in Settings or install 'blup'.")
        }

        val scriptFile = ScriptResourceUtils.extractResourceScript("python/probe_server.py", "blender_probe_server")
        val projectPath = project.basePath ?: ""
        val addonName = BlenderProbeUtils.detectAddonModuleName(project)
        val sourceRoot = BlenderProbeUtils.getAddonSourceRoot(project) ?: projectPath

        val cmd = GeneralCommandLine()
            .withExePath(blenderPath)
            .withParameters("--factory-startup", "--python-exit-code", "1", "-P", scriptFile.absolutePath)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(projectPath)
            .withEnvironment("BLENDER_PROBE_PROJECT_ROOT", sourceRoot)
            .withEnvironment("BLENDER_PROBE_ADDON_NAME", addonName)
            .withEnvironment("PYTHONUNBUFFERED", "1")

        val port = debugPort
        val pyPath = pydevdPath

        if (port != null && pyPath != null) {
            cmd.withEnvironment("BLENDER_PROBE_DEBUG_PORT", port.toString())
            cmd.withEnvironment("BLENDER_PROBE_PYDEVD_PATH", pyPath)
        }

        val processHandler = object : OSProcessHandler(cmd) {
            override fun readerOptions(): BaseOutputReader.Options {
                return BaseOutputReader.Options.forMostlySilentProcess()
            }
        }

        processHandler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = event.text
                text.lines().forEach { line ->
                    val cleanLine = line.trim()
                    if (cleanLine.startsWith("BLENDER_PROBE_PORT::")) {
                        val portStr = cleanLine.removePrefix("BLENDER_PROBE_PORT::")
                        try {
                            val port = portStr.toInt()
                            BlenderProbeManager.updatePort(port)
                        } catch (e: NumberFormatException) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                BlenderProbeManager.activePort = null
            }

            override fun startNotified(event: ProcessEvent) {}
        })

        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }
}