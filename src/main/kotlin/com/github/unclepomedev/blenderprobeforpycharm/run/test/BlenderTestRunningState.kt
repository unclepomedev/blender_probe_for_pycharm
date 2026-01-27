package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
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
import java.io.File
import java.nio.charset.StandardCharsets

class BlenderTestRunningState(
    environment: ExecutionEnvironment,
    private val configuration: BlenderTestRunConfiguration
) : CommandLineState(environment) {
    var cachedBlenderPath: String? = null
    var cachedAddonName: String? = null
    var cachedSourceRoot: String? = null

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor, processHandler)
        return DefaultExecutionResult(console, processHandler)
    }

    override fun startProcess(): ProcessHandler {
        val project = environment.project
        val blenderPath = cachedBlenderPath ?: BlenderSettings.getInstance(project).resolveBlenderPath()

        if (blenderPath.isNullOrEmpty()) {
            throw ExecutionException("Blender executable not found. Please configure it in Settings or ensure 'blup' is installed.")
        }

        val testDir = configuration.testDir
        if (testDir.isEmpty()) {
            throw ExecutionException("Test directory is not specified in Run Configuration.")
        }

        val basePath = project.basePath ?: throw ExecutionException("Project base path is invalid.")
        val projectScript = File(basePath, "tests/run_tests.py")
        val scriptFile = if (projectScript.exists()) {
            projectScript
        } else {
            ScriptResourceUtils.extractResourceScript("python/run_tests.py", "blender_test_runner")
        }

        val sourceRoot = cachedSourceRoot ?: BlenderProbeUtils.getAddonSourceRoot(project) ?: basePath
        val addonName = cachedAddonName ?: BlenderProbeUtils.detectAddonModuleName(project)

        val cmd = GeneralCommandLine()
            .withExePath(blenderPath)
            .withParameters("-b", "--factory-startup", "-P", scriptFile.absolutePath, "--", testDir)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(basePath)
            .withEnvironment("BLENDER_PROBE_PROJECT_ROOT", sourceRoot)
            .withEnvironment("BLENDER_PROBE_ADDON_NAME", addonName)
            .withEnvironment("PYTHONDONTWRITEBYTECODE", "1")

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