package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderAddonDetectionService
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

/**
 * Base program runner for executing Blender tasks (applications, tests, etc.). Encapsulates the
 * common preparation, path resolution, and execution orchestration.
 */
abstract class BaseBlenderRunner<T> : AsyncProgramRunner<RunnerSettings>()
    where T : CommandLineState, T : BlenderExecutionState {

    /**
     * Checks if the given state is supported by this runner and casts it to [T], or returns null if
     * not supported.
     */
    protected abstract fun checkAndCastState(state: RunProfileState): T?

    /** Title displayed in the background task progress while preparing execution. */
    protected abstract val preparationTaskTitle: String

    /** Hook called synchronously before queuing the background preparation task. */
    protected open fun preExecute(environment: ExecutionEnvironment, state: T) {}

    /** Starts the execution session after state preparation. */
    protected abstract fun startSession(
        environment: ExecutionEnvironment,
        state: T,
    ): RunContentDescriptor

    /**
     * Optional hook for runner-specific preparation after common blender and addon paths are
     * resolved.
     */
    protected open fun postPrepareExecutionState(
        project: Project,
        state: T,
        executorId: String,
    ) {}

    override fun execute(
        environment: ExecutionEnvironment,
        state: RunProfileState,
    ): Promise<RunContentDescriptor?> {
        val promise = AsyncPromise<RunContentDescriptor?>()

        val castState = checkAndCastState(state)
        if (castState == null) {
            promise.setResult(null)
            return promise
        }

        try {
            preExecute(environment, castState)
        } catch (e: Exception) {
            promise.setError(e)
            return promise
        }

        queuePreparationTask(environment, castState, promise)

        return promise
    }

    private fun queuePreparationTask(
        environment: ExecutionEnvironment,
        state: T,
        promise: AsyncPromise<RunContentDescriptor?>,
    ) {
        object : Task.Backgroundable(environment.project, preparationTaskTitle, true) {
                override fun run(indicator: ProgressIndicator) {
                    prepareExecutionState(environment.project, state, environment.executor.id)
                }

                override fun onSuccess() {
                    if (promise.state == Promise.State.REJECTED) return
                    try {
                        val descriptor = startSession(environment, state)
                        promise.setResult(descriptor)
                    } catch (e: Exception) {
                        promise.setError(e)
                    }
                }

                override fun onThrowable(error: Throwable) {
                    promise.setError(error)
                }
            }
            .queue()
    }

    protected open fun prepareExecutionState(
        project: Project,
        state: T,
        executorId: String,
    ) {
        val path =
            BlenderSettings.getInstance(project).resolveBlenderPath()
                ?: throw ExecutionException("Blender executable not found. Check settings.")

        state.cachedBlenderPath = path

        ApplicationManager.getApplication().runReadAction {
            val detectionService = BlenderAddonDetectionService.getInstance(project)
            state.cachedAddonName = detectionService.getAddonModuleName()
            state.cachedSourceRoot = detectionService.getAddonSourceRoot() ?: project.basePath
        }

        postPrepareExecutionState(project, state, executorId)
    }

    /** Standard implementation for starting a run session using [CommandLineState.execute]. */
    protected fun startRunSession(
        state: T,
        environment: ExecutionEnvironment,
    ): RunContentDescriptor {
        val executionResult = state.execute(environment.executor, this)
        return RunContentBuilder(executionResult, environment)
            .showRunContent(environment.contentToReuse)
    }
}
