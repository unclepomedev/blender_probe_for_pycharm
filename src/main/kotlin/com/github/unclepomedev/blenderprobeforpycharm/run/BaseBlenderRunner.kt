package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.manifest.BlenderManifestDetector
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.github.unclepomedev.blenderprobeforpycharm.ui.BlenderNotificationUtils
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
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

/** Interface representing execution states that need Blender binary and add-on path caching. */
interface BlenderExecutionState {
    var cachedBlenderPath: String?
    var cachedAddonName: String?
    var cachedSourceRoot: String?
}

/**
 * Base program runner for executing Blender processes asynchronously. Encapsulates the shared
 * preparation logic (document saving, background task scheduling, Blender binary resolution, and
 * add-on detection).
 */
abstract class BaseBlenderRunner<T : BlenderExecutionState> : AsyncProgramRunner<RunnerSettings>() {

    protected abstract val stateClass: Class<T>

    protected open val taskTitle: String = "Preparing Blender execution..."

    override fun execute(
        environment: ExecutionEnvironment,
        state: RunProfileState,
    ): Promise<RunContentDescriptor?> {
        val promise = AsyncPromise<RunContentDescriptor?>()

        if (!stateClass.isInstance(state)) {
            promise.setResult(null)
            return promise
        }

        val typedState = stateClass.cast(state)

        saveAllDocuments()
        queuePreparationTask(environment, typedState, promise)

        return promise
    }

    protected open fun saveAllDocuments() {
        ApplicationManager.getApplication().invokeAndWait {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
    }

    private fun queuePreparationTask(
        environment: ExecutionEnvironment,
        state: T,
        promise: AsyncPromise<RunContentDescriptor?>,
    ) {
        object : Task.Backgroundable(environment.project, taskTitle, true) {
                override fun run(indicator: ProgressIndicator) {
                    prepareExecutionState(environment.project, state, environment)
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

                override fun onCancel() {
                    promise.cancel()
                }
            }
            .queue()
    }

    protected open fun prepareExecutionState(
        project: Project,
        state: T,
        environment: ExecutionEnvironment,
    ) {
        val path =
            BlenderSettings.getInstance(project).resolveBlenderPath()
                ?: run {
                    val message = "Blender executable not found. Check settings."
                    BlenderNotificationUtils.showNotificationWithSettings(
                        project,
                        "Blender Not Found",
                        message,
                    )
                    throw ExecutionException(message)
                }

        state.cachedBlenderPath = path

        ApplicationManager.getApplication().runReadAction {
            val detection = BlenderManifestDetector.detectAddon(project)
            if (!detection.isResolved) {
                val message = "Failed to resolve add-on:\n" + detection.formatMessage()
                BlenderNotificationUtils.showNotificationWithSettings(
                    project,
                    "Addon Detection Failed",
                    detection.formatMessage(),
                )
                throw ExecutionException(message)
            }
            state.cachedAddonName = detection.moduleName
            state.cachedSourceRoot = detection.sourceRoot ?: project.basePath
        }
    }

    protected abstract fun startSession(
        environment: ExecutionEnvironment,
        state: T,
    ): RunContentDescriptor?

    protected fun buildDefaultRunContentDescriptor(
        environment: ExecutionEnvironment,
        state: RunProfileState,
    ): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this) ?: return null
        return RunContentBuilder(executionResult, environment)
            .showRunContent(environment.contentToReuse)
    }
}
