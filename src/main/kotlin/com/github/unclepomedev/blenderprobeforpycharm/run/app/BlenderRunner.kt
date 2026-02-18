package com.github.unclepomedev.blenderprobeforpycharm.run.app

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebugSessionListener
import com.jetbrains.python.debugger.PyDebugProcess
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.io.File
import java.net.ServerSocket

class BlenderRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "BlenderRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return (executorId == DefaultRunExecutor.EXECUTOR_ID || executorId == DefaultDebugExecutor.EXECUTOR_ID)
                && profile is BlenderRunConfiguration
    }

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        val promise = AsyncPromise<RunContentDescriptor?>()

        if (state !is BlenderRunningState) {
            promise.setResult(null)
            return promise
        }

        object : Task.Backgroundable(environment.project, "Preparing Blender execution...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val project = environment.project
                    val path = BlenderSettings.getInstance(project).resolveBlenderPath()
                        ?: throw ExecutionException("Blender executable not found. Check settings.")

                    state.cachedBlenderPath = path

                    ApplicationManager.getApplication().runReadAction {
                        state.cachedAddonName = BlenderProbeUtils.detectAddonModuleName(project)
                        state.cachedSourceRoot = BlenderProbeUtils.getAddonSourceRoot(project) ?: project.basePath
                    }
                    if (environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
                        val pydevd = findPydevdPath()
                            ?: throw ExecutionException("Could not find 'pydevd'. Debugging not possible.")
                        state.pydevdPath = pydevd
                    }

                } catch (e: Exception) {
                    promise.setError(e)
                }
            }

            override fun onSuccess() {
                if (promise.state == Promise.State.REJECTED) return

                try {
                    val descriptor = if (environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
                        startDebugSession(state, environment)
                    } else {
                        startRunSession(state, environment)
                    }
                    promise.setResult(descriptor)
                } catch (e: Exception) {
                    promise.setError(e)
                }
            }

            override fun onThrowable(error: Throwable) {
                promise.setError(error)
            }
        }.queue()

        return promise
    }

    private fun startRunSession(state: BlenderRunningState, environment: ExecutionEnvironment): RunContentDescriptor {
        val executionResult = state.execute(environment.executor, this)
        return RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
    }

    @Suppress("DEPRECATION")
    private fun startDebugSession(state: BlenderRunningState, environment: ExecutionEnvironment): RunContentDescriptor {
        val serverSocket = ServerSocket(0)
        var createdProcessHandler: ProcessHandler? = null

        try {
            state.debugPort = serverSocket.localPort

            val session = XDebuggerManager.getInstance(environment.project)
                .startSession(environment, object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession): XDebugProcess {
                        val executionResult = state.execute(environment.executor, this@BlenderRunner)
                        createdProcessHandler = executionResult.processHandler
                        return PyDebugProcess(
                            session,
                            serverSocket,
                            executionResult.executionConsole,
                            executionResult.processHandler,
                            false
                        )
                    }
                })

            session.addSessionListener(object : XDebugSessionListener {
                override fun sessionStopped() {
                    if (createdProcessHandler != null && !createdProcessHandler.isProcessTerminated) {
                        createdProcessHandler.destroyProcess()
                    }
                }
            })

            return session.runContentDescriptor

        } catch (e: Exception) {
            if (createdProcessHandler != null && !createdProcessHandler.isProcessTerminated) {
                createdProcessHandler.destroyProcess()
            }
            if (!serverSocket.isClosed) {
                serverSocket.close()
            }
            throw e
        }
    }

    private fun findPydevdPath(): String? {
        val pluginIds = listOf("Pythonid", "PythonCore", "python-ce", "python")

        for (id in pluginIds) {
            val pluginId = PluginId.getId(id)
            val plugin = PluginManagerCore.getPlugin(pluginId)

            if (plugin != null && !PluginManagerCore.isDisabled(pluginId)) {
                val path = plugin.pluginPath.resolve("helpers/pydev").toFile()
                if (path.exists()) return path.absolutePath
            }
        }

        val possiblePaths = listOf(
            File(PathManager.getHomePath(), "plugins/python/helpers/pydev"),
            File(PathManager.getHomePath(), "plugins/python-ce/helpers/pydev"),
            File(PathManager.getHomePath(), "plugins/PythonCore/helpers/pydev"),
            File(PathManager.getHomePath(), "Contents/plugins/python/helpers/pydev"),
            File(PathManager.getHomePath(), "Contents/plugins/python-ce/helpers/pydev")
        )

        for (path in possiblePaths) {
            if (path.exists()) return path.absolutePath
        }

        return null
    }
}