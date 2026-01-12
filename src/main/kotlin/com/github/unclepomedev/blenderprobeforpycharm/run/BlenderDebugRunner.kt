package com.github.unclepomedev.blenderprobeforpycharm.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.debugger.PyDebugProcess
import java.io.File
import java.net.ServerSocket

class BlenderDebugRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "BlenderDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is BlenderRunConfiguration
    }

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (state !is BlenderRunningState) return null

        val serverSocket = ServerSocket(0)
        val debugPort = serverSocket.localPort

        val pydevdPath = findPydevdPath()
        if (pydevdPath != null) {
            state.debugPort = debugPort
            state.pydevdPath = pydevdPath
        } else {
            throw com.intellij.execution.ExecutionException("Could not find 'pydevd' in PyCharm plugins. Debugging is not possible.")
        }

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

    private fun findPydevdPath(): String? {
        val pluginIds = listOf("PythonCore", "python-ce", "python")

        for (id in pluginIds) {
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
            if (plugin != null) {
                val path = plugin.pluginPath.resolve("helpers/pydev").toFile()
                if (path.exists()) return path.absolutePath
            }
        }

        val possiblePaths = listOf(
            File(PathManager.getHomePath(), "plugins/python-ce/helpers/pydev"),
            File(PathManager.getHomePath(), "plugins/PythonCore/helpers/pydev"),
            File(PathManager.getHomePath(), "Contents/plugins/python-ce/helpers/pydev"),
            File(PathManager.getHomePath(), "Contents/plugins/PythonCore/helpers/pydev")
        )

        for (path in possiblePaths) {
            if (path.exists()) return path.absolutePath
        }

        return null
    }
}