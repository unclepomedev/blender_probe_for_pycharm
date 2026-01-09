package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

class BlenderTestRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<BlenderTestRunConfigurationOptions>(project, factory, name) {

    var testDir: String
        get() = options.testDir
        set(value) {
            options.testDir = value
        }

    override fun getOptions(): BlenderTestRunConfigurationOptions {
        return super.getOptions() as BlenderTestRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return BlenderTestSettingsEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return BlenderTestRunningState(environment, this)
    }

    override fun checkConfiguration() {
        super.checkConfiguration()
        val settings = BlenderSettings.getInstance(project)
        if (settings.state.blenderPath.isEmpty()) {
            throw RuntimeConfigurationException("Blender executable path is not set.")
        }
        if (testDir.isEmpty()) {
            throw RuntimeConfigurationException("Test directory is not specified.")
        }
    }
}
