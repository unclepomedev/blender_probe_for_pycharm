package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.icons.BlenderProbeIcons
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.NotNullLazyValue

class BlenderTestConfigurationType : ConfigurationTypeBase(
    ID,
    "Blender Test",
    "Run Blender tests",
    NotNullLazyValue.createValue { BlenderProbeIcons.Logo16_2 }
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