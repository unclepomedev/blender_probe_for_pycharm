package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

class BlenderTestRunConfigurationOptions : RunConfigurationOptions() {
    private val testDirProperty: StoredProperty<String?> = string("").provideDelegate(this, "testDir")

    var testDir: String
        get() = testDirProperty.getValue(this) ?: ""
        set(value) {
            testDirProperty.setValue(this, value)
        }
}
