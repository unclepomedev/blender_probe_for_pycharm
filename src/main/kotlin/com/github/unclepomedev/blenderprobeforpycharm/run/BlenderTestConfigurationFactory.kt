package com.github.unclepomedev.blenderprobeforpycharm.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

class BlenderTestConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return BlenderTestRunConfiguration(project, this, "Blender Test")
    }

    override fun getId(): String {
        return "BlenderTestConfigurationFactory"
    }

    override fun getOptionsClass(): Class<out com.intellij.execution.configurations.RunConfigurationOptions> {
        return BlenderTestRunConfigurationOptions::class.java
    }
}
