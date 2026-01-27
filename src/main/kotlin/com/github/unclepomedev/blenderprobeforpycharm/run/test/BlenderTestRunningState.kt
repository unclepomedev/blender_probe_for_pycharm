package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.github.unclepomedev.blenderprobeforpycharm.ScriptResourceUtils
import com.github.unclepomedev.blenderprobeforpycharm.run.common.AbstractBlenderRunningState
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.util.io.BaseOutputReader
import java.io.File

class BlenderTestRunningState(
    environment: ExecutionEnvironment,
    private val configuration: BlenderTestRunConfiguration
) : AbstractBlenderRunningState(environment) {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor, processHandler)
        return DefaultExecutionResult(console, processHandler)
    }

    override fun startProcess(): ProcessHandler {
        val cmd = createBaseCommandLine()

        val testDir = configuration.testDir
        if (testDir.isEmpty()) {
            throw ExecutionException("Test directory is not specified in Run Configuration.")
        }

        val projectScript = File(environment.project.basePath, "tests/run_tests.py")
        val scriptFile = if (projectScript.exists()) {
            projectScript
        } else {
            ScriptResourceUtils.extractResourceScript("python/run_tests.py", "blender_test_runner")
        }

        cmd.addParameters("-b", "--factory-startup", "-P", scriptFile.absolutePath, "--", testDir)

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