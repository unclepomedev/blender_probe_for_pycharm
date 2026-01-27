package com.github.unclepomedev.blenderprobeforpycharm.run.app

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.ScriptResourceUtils
import com.github.unclepomedev.blenderprobeforpycharm.run.common.AbstractBlenderRunningState
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader

class BlenderRunningState(
    environment: ExecutionEnvironment,
) : AbstractBlenderRunningState(environment) {

    var debugPort: Int? = null
    var pydevdPath: String? = null

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor)
        console?.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler)
    }

    override fun startProcess(): ProcessHandler {
        val cmd = createBaseCommandLine()
        val scriptFile = ScriptResourceUtils.extractResourceScript("python/probe_server.py", "blender_probe_server")
        cmd.addParameters("--factory-startup", "--python-exit-code", "1", "-P", scriptFile.absolutePath)

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