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
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.xdebugger.*
import com.jetbrains.python.debugger.PyDebugProcess
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.ServerSocket

/**
 * Program runner for executing Blender.
 * Supports both Run and Debug modes. In Debug mode, it attaches the Python debugger.
 */
class BlenderRunner : AsyncProgramRunner<RunnerSettings>() {

    override fun getRunnerId(): String = "BlenderRunner"
    private val log = Logger.getInstance(BlenderRunner::class.java)

    /**
     * Checks if the runner can execute the given run profile.
     *
     * @param executorId The ID of the executor (Run or Debug).
     * @param profile The run profile to check.
     * @return True if the runner can execute the profile, false otherwise.
     */
    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return (executorId == DefaultRunExecutor.EXECUTOR_ID || executorId == DefaultDebugExecutor.EXECUTOR_ID)
                && profile is BlenderRunConfiguration
    }

    /**
     * Executes the run profile asynchronously.
     *
     * @param environment The execution environment.
     * @param state The run profile state.
     * @return A promise that resolves to the run content descriptor.
     */
    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        val promise = AsyncPromise<RunContentDescriptor?>()

        if (state !is BlenderRunningState) {
            promise.setResult(null)
            return promise
        }

        object : Task.Backgroundable(environment.project, "Preparing Blender execution...", true) {
            override fun run(indicator: ProgressIndicator) {
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

    private fun startDebugSession(state: BlenderRunningState, environment: ExecutionEnvironment): RunContentDescriptor {
        val serverSocket = ServerSocket(0)
        var createdProcessHandler: ProcessHandler? = null
        val project = environment.project

        val processStarter = object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                val executionResult = state.execute(environment.executor, this@BlenderRunner)
                createdProcessHandler = executionResult.processHandler
                session.addSessionListener(object : XDebugSessionListener {
                    override fun sessionStopped() {
                        createdProcessHandler?.takeUnless { it.isProcessTerminated }?.destroyProcess()
                        try {
                            if (!serverSocket.isClosed) serverSocket.close()
                        } catch (e: Exception) {
                            log.warn("Failed to close debug server socket", e)
                        }
                    }
                })
                return PyDebugProcess(
                    session, serverSocket, executionResult.executionConsole,
                    executionResult.processHandler, false
                )
            }
        }

        try {
            state.debugPort = serverSocket.localPort
            val manager = XDebuggerManager.getInstance(project)
            if (isAtLeast2026()) {
                try {
                    return debugSession2026(manager, environment, processStarter)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                } catch (e: ReflectiveOperationException) {
                    log.error("Blender Probe: Failed to use 2026.X Debugger API, falling back to legacy API. Error: ${e.message}")
                }
            }
            // 2025.x or fallback
            return debugSession2025(manager, environment, processStarter)

        } catch (e: Exception) {
            createdProcessHandler?.takeUnless { it.isProcessTerminated }?.destroyProcess()
            if (!serverSocket.isClosed) serverSocket.close()
            throw e
        }
    }

    //TODO: (HACK) using reflection to get the 2025 version to compile. change the implementation once drop the 2025 version.
    private fun debugSession2026(
        manager: XDebuggerManager,
        environment: ExecutionEnvironment,
        processStarter: XDebugProcessStarter
    ): RunContentDescriptor {
        val builder = manager.javaClass.getMethod("newSessionBuilder", XDebugProcessStarter::class.java)
            .invoke(manager, processStarter)
        val environmentMethod = builder.javaClass.getMethod("environment", ExecutionEnvironment::class.java)
        val startSessionMethod = builder.javaClass.getMethod("startSession")
        val descriptorMethod = startSessionMethod.returnType.getMethod("getRunContentDescriptor")
        environmentMethod.invoke(builder, environment)
        val result = startSessionMethod.invoke(builder)
        return descriptorMethod.invoke(result) as? RunContentDescriptor
            ?: throw ExecutionException("Debug session started but returned no content descriptor")
    }

    @Suppress("DEPRECATION")
    private fun debugSession2025(
        manager: XDebuggerManager,
        environment: ExecutionEnvironment,
        processStarter: XDebugProcessStarter
    ): RunContentDescriptor {
        val session = manager.startSession(environment, processStarter)
        return session.runContentDescriptor
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
            File(PathManager.getHomePath(), "Contents/plugins/python-ce/helpers/pydev"),
            File(PathManager.getHomePath(), "Contents/plugins/PythonCore/helpers/pydev")
        )

        for (path in possiblePaths) {
            if (path.exists()) return path.absolutePath
        }

        return null
    }

    private fun isAtLeast2026(): Boolean {
        val build = ApplicationInfo.getInstance().build
        return build.baselineVersion >= 261
    }
}
