package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.icons.BlenderProbeIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import javax.swing.Icon

class BlenderRunConfigurationType : ConfigurationType, DumbAware {
    override fun getIcon(): Icon = BlenderProbeIcons.Logo16
    override fun getDisplayName(): String = "Blender Probe (Dev)"
    override fun getConfigurationTypeDescription(): String = "Run blender with Probe Server for development"
    override fun getId(): String = "BlenderProbeRunConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(BlenderProbeRunConfigurationFactory(this))
    }
}

class BlenderProbeRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return BlenderRunConfiguration(project, this, "Blender Probe")
    }

    override fun getId(): String = "BlenderProbeRunFactory"

    override fun getOptionsClass() = com.intellij.execution.configurations.RunConfigurationOptions::class.java
}