package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
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
import java.nio.charset.StandardCharsets

class BlenderRunningState(
    environment: ExecutionEnvironment,
    private val configuration: BlenderRunConfiguration
) : CommandLineState(environment) {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor)
        console?.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler)
    }

    override fun startProcess(): ProcessHandler {
        val project = environment.project
        val settings = BlenderSettings.getInstance(project)
        val blenderPath = settings.state.blenderPath

        if (blenderPath.isEmpty()) {
            throw ExecutionException("Blender executable path is not set.")
        }

        val scriptFile = ScriptResourceUtils.extractResourceScript("python/probe_server.py", "blender_probe_server")
        val projectPath = project.basePath ?: ""

        val cmd = GeneralCommandLine()
            .withExePath(blenderPath)
            .withParameters("--factory-startup", "--python-exit-code", "1", "-P", scriptFile.absolutePath)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(project.basePath)
            .withEnvironment("BLENDER_PROBE_PROJECT_ROOT", projectPath)
            .withEnvironment("PYTHONUNBUFFERED", "1")

        val processHandler = OSProcessHandler(cmd)

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
                            println("DEBUG: PyCharm caught port $port")
                        } catch (e: NumberFormatException) {
                            // ignore
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