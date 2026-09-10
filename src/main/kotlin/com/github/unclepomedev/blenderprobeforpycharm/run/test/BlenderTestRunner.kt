package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderAddonDetectionService
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

/**
 * Program runner for executing Blender tests. This runner handles the execution of tests within the
 * Blender environment.
 */
class BlenderTestRunner : AsyncProgramRunner<RunnerSettings>() {
    /**
     * Returns the unique ID of this runner.
     *
     * @return The runner ID.
     */
    override fun getRunnerId(): String = "BlenderTestRunner"

    /**
     * Checks if the runner can execute the given run profile. Only supports the standard Run
     * executor and BlenderTestRunConfiguration.
     *
     * @param executorId The ID of the executor.
     * @param profile The run profile to check.
     * @return True if the runner can execute the profile, false otherwise.
     */
    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultRunExecutor.EXECUTOR_ID &&
            profile is BlenderTestRunConfiguration
    }

    /**
     * Executes the run profile asynchronously. Prepares the Blender environment, resolves paths,
     * and starts the test execution.
     *
     * @param environment The execution environment.
     * @param state The run profile state.
     * @return A promise that resolves to the run content descriptor.
     */
    override fun execute(
        environment: ExecutionEnvironment,
        state: RunProfileState,
    ): Promise<RunContentDescriptor?> {
        val promise = AsyncPromise<RunContentDescriptor?>()

        if (state !is BlenderTestRunningState) {
            promise.setResult(null)
            return promise
        }

        saveAllDocuments()
        queuePreparationTask(environment, state, promise)

        return promise
    }

    private fun saveAllDocuments() {
        ApplicationManager.getApplication().invokeAndWait {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
    }

    private fun queuePreparationTask(
        environment: ExecutionEnvironment,
        state: BlenderTestRunningState,
        promise: AsyncPromise<RunContentDescriptor?>,
    ) {
        object :
                Task.Backgroundable(
                    environment.project,
                    "Preparing blender test execution...",
                    true,
                ) {
                override fun run(indicator: ProgressIndicator) {
                    prepareExecutionState(environment.project, state)
                }

                override fun onSuccess() {
                    try {
                        val descriptor = startTestSession(environment, state)
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

    private fun prepareExecutionState(
        project: Project,
        state: BlenderTestRunningState,
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
    }

    private fun startTestSession(
        environment: ExecutionEnvironment,
        state: BlenderTestRunningState,
    ): RunContentDescriptor {
        val executionResult = state.execute(environment.executor, this)
        return RunContentBuilder(executionResult, environment)
            .showRunContent(environment.contentToReuse)
    }
}
