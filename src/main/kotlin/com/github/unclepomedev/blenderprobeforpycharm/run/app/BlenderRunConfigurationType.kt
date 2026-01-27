package com.github.unclepomedev.blenderprobeforpycharm.run.app

import com.github.unclepomedev.blenderprobeforpycharm.icons.BlenderProbeIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import javax.swing.Icon

class BlenderRunConfigurationType : ConfigurationType, DumbAware {
    override fun getIcon(): Icon = BlenderProbeIcons.Logo16
    override fun getDisplayName(): String = "Blender Probe (Dev)"
    override fun getConfigurationTypeDescription(): String = "Run blender with Probe Server for development"
    override fun getId(): String = "BlenderProbeRunConfiguration"

    private val factory: ConfigurationFactory = BlenderProbeRunConfigurationFactory(this)

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(factory)
    }
}

class BlenderProbeRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return BlenderRunConfiguration(project, this, "Blender Probe")
    }

    override fun getId(): String = "BlenderProbeRunFactory"

    override fun getOptionsClass() = RunConfigurationOptions::class.java
}