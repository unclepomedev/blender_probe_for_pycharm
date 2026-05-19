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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.debugger.PyDebugProcess
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
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
                    state.pydevdPath = PythonHelper.DEBUGGER.pythonPathEntry
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

    private fun startDebugSession(
        state: BlenderRunningState,
        environment: ExecutionEnvironment
    ): RunContentDescriptor {
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
            @Suppress("UnstableApiUsage")  // newSessionBuilder is experimental
            val session = XDebuggerManager.getInstance(project)
                .newSessionBuilder(processStarter)
                .environment(environment)
                .startSession()
            @Suppress("UnstableApiUsage")  // getRunContentDescriptor is experimental
            return session.runContentDescriptor
                ?: throw ExecutionException("Debug session started but returned no content descriptor")

        } catch (e: Exception) {
            createdProcessHandler?.takeUnless { it.isProcessTerminated }?.destroyProcess()
            try {
                if (!serverSocket.isClosed) serverSocket.close()
            } catch (closeEx: Exception) {
                e.addSuppressed(closeEx)
            }
            throw e
        }
    }
}
