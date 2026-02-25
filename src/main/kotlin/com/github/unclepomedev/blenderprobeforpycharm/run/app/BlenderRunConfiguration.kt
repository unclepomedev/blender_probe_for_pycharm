package com.github.unclepomedev.blenderprobeforpycharm.run.app

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * Run configuration for running Blender with the Probe Server.
 * This configuration does not require user settings as it uses the project-level Blender settings.
 */
class BlenderRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    /**
     * Returns the editor for this configuration.
     *
     * @return The configuration editor.
     */
    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return BlenderRunConfigurationEditor()
    }

    /**
     * Prepares the run profile state for execution.
     *
     * @param executor The executor (Run or Debug).
     * @param environment The execution environment.
     * @return The run profile state.
     */
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return BlenderRunningState(environment)
    }
}