package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

/**
 * Options for the Blender Test run configuration.
 * Stores persistent settings such as the test directory.
 */
class BlenderTestRunConfigurationOptions : RunConfigurationOptions() {
    private val testDirProperty: StoredProperty<String?> = string("").provideDelegate(this, "testDir")

    /**
     * The directory containing the tests to be executed.
     */
    var testDir: String
        get() = testDirProperty.getValue(this) ?: ""
        set(value) {
            testDirProperty.setValue(this, value)
        }
}
