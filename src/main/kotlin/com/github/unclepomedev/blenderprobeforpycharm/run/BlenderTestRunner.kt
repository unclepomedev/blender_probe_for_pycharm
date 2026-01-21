package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

class BlenderTestRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = "BlenderTestRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultRunExecutor.EXECUTOR_ID && profile is BlenderTestRunConfiguration
    }

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        val promise = AsyncPromise<RunContentDescriptor?>()

        if (state !is BlenderTestRunningState) {
            promise.setResult(null)
            return promise
        }

        object : Task.Backgroundable(environment.project, "Preparing blender test execution...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val path = BlenderSettings.getInstance(project).resolveBlenderPath()
                        ?: throw ExecutionException("Blender executable not found. Check settings.")
                    state.cachedBlenderPath = path

                    ApplicationManager.getApplication().runReadAction {
                        state.cachedAddonName = BlenderProbeUtils.detectAddonModuleName(project)
                        state.cachedSourceRoot = BlenderProbeUtils.getAddonSourceRoot(project) ?: project.basePath
                    }

                } catch (e: Exception) {
                    promise.setError(e)
                    throw e
                }
            }

            override fun onSuccess() {
                try {
                    val executionResult = state.execute(environment.executor, this@BlenderTestRunner)
                    val descriptor = RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
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
}