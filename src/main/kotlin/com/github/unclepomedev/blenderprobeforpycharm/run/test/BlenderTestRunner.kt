package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.github.unclepomedev.blenderprobeforpycharm.run.BaseBlenderRunner
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * Program runner for executing Blender tests. This runner handles the execution of tests within the
 * Blender environment.
 */
class BlenderTestRunner : BaseBlenderRunner<BlenderTestRunningState>() {
    /**
     * Returns the unique ID of this runner.
     *
     * @return The runner ID.
     */
    override fun getRunnerId(): String = "BlenderTestRunner"

    override val preparationTaskTitle: String = "Preparing blender test execution..."

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

    override fun checkAndCastState(state: RunProfileState): BlenderTestRunningState? =
        state as? BlenderTestRunningState

    override fun preExecute(environment: ExecutionEnvironment, state: BlenderTestRunningState) {
        saveAllDocuments()
    }

    override fun startSession(
        environment: ExecutionEnvironment,
        state: BlenderTestRunningState,
    ): RunContentDescriptor = startRunSession(state, environment)

    private fun saveAllDocuments() {
        ApplicationManager.getApplication().invokeAndWait {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
    }
}
