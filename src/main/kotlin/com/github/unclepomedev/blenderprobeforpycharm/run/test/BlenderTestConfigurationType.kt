package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.github.unclepomedev.blenderprobeforpycharm.icons.BlenderProbeIcons
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.NotNullLazyValue

/**
 * Configuration type for the Blender Test run configuration.
 * Defines the "Blender Test" entry in the "Run/Debug Configurations" dialog.
 */
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

        /**
         * Retrieves the instance of BlenderTestConfigurationType.
         *
         * @return The configuration type instance.
         */
        fun getInstance(): BlenderTestConfigurationType {
            return ConfigurationTypeUtil.findConfigurationType(BlenderTestConfigurationType::class.java)
        }
    }
}