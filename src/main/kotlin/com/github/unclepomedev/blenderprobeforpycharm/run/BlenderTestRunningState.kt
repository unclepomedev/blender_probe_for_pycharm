package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.ScriptResourceUtils
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.util.io.BaseOutputReader
import java.nio.charset.StandardCharsets

class BlenderTestRunningState(
    environment: ExecutionEnvironment,
    private val configuration: BlenderTestRunConfiguration
) : CommandLineState(environment) {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor, processHandler)
        return DefaultExecutionResult(console, processHandler)
    }

    override fun startProcess(): ProcessHandler {
        val project = environment.project
        val settings = BlenderSettings.getInstance(project)
        val blenderPath = settings.state.blenderPath

        if (blenderPath.isEmpty()) {
            throw ExecutionException("Blender executable path is not set. Please check Settings > Tools > Blender Probe.")
        }

        val testDir = configuration.testDir
        if (testDir.isEmpty()) {
            throw ExecutionException("Test directory is not specified in Run Configuration.")
        }

        val scriptFile = ScriptResourceUtils.extractResourceScript("python/run_tests.py", "blender_test_runner")
        val projectPath = project.basePath ?: ""

        val cmd = GeneralCommandLine()
            .withExePath(blenderPath)
            .withParameters("-b", "--factory-startup", "-P", scriptFile.absolutePath, "--", testDir)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(project.basePath)
            .withEnvironment("BLENDER_PROBE_PROJECT_ROOT", projectPath)

        val processHandler = object : OSProcessHandler(cmd) {
            override fun readerOptions(): BaseOutputReader.Options {
                return BaseOutputReader.Options.forMostlySilentProcess()
            }
        }
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }

    private fun createConsole(executor: Executor, processHandler: ProcessHandler): ConsoleView {
        val properties = BlenderTestConsoleProperties(configuration, executor)

        return SMTestRunnerConnectionUtil.createAndAttachConsole(
            "BlenderTest",
            processHandler,
            properties
        )
    }
}
