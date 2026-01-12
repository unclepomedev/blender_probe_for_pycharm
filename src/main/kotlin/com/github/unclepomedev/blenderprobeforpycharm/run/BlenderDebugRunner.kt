package com.github.unclepomedev.blenderprobeforpycharm.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.debugger.PyDebugProcess
import java.net.ServerSocket

class BlenderDebugRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "BlenderDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is BlenderRunConfiguration
    }

    @Suppress("UnstableApiUsage")
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (state !is BlenderRunningState) return null

        val serverSocket = ServerSocket(0)
        val debugPort = serverSocket.localPort

        val pydevdPath = PythonHelpersLocator.findPathInHelpers("pydev").toString()
        state.debugPort = debugPort
        state.pydevdPath = pydevdPath

        val executionResult = state.execute(environment.executor, this)

        return XDebuggerManager.getInstance(environment.project)
            .startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess {
                    return PyDebugProcess(
                        session,
                        serverSocket,
                        executionResult.executionConsole,
                        executionResult.processHandler,
                        false
                    )
                }
            }).runContentDescriptor
    }
}