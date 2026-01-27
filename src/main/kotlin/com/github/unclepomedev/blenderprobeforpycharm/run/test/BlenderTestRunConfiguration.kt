package com.github.unclepomedev.blenderprobeforpycharm.run.test

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
        if (testDir.isEmpty()) {
            throw RuntimeConfigurationException("Test directory is not specified.")
        }
    }
}