package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings

class BlenderTestRunningStateTest : BaseBlenderTest() {

    fun testFactoryStartupFlagIncludedWhenEnabled() {
        BlenderSettings.getInstance(project).state.useFactoryStartup = true

        val params = BlenderTestRunningState.buildParameters(
            useFactoryStartup = BlenderSettings.getInstance(project).state.useFactoryStartup,
            scriptPath = "/tmp/run_tests.py",
            testDir = "/tmp/tests"
        )

        assertTrue("--factory-startup should be present when the setting is enabled", "--factory-startup" in params)
    }

    fun testFactoryStartupFlagOmittedWhenDisabled() {
        BlenderSettings.getInstance(project).state.useFactoryStartup = false

        val params = BlenderTestRunningState.buildParameters(
            useFactoryStartup = BlenderSettings.getInstance(project).state.useFactoryStartup,
            scriptPath = "/tmp/run_tests.py",
            testDir = "/tmp/tests"
        )

        assertFalse("--factory-startup should be absent when the setting is disabled", "--factory-startup" in params)
    }
}