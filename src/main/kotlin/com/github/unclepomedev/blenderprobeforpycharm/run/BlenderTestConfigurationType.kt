package com.github.unclepomedev.blenderprobeforpycharm.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NotNullLazyValue

class BlenderTestConfigurationType : ConfigurationTypeBase(
    ID,
    "Blender Test",
    "Run Blender tests",
    NotNullLazyValue.createValue { AllIcons.Nodes.Test }
) {
    init {
        addFactory(BlenderTestConfigurationFactory(this))
    }

    companion object {
        const val ID = "BlenderTestRunConfiguration"

        fun getInstance(): BlenderTestConfigurationType {
            return ConfigurationTypeUtil.findConfigurationType(BlenderTestConfigurationType::class.java)
        }
    }
}