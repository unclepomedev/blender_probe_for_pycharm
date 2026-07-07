package com.github.unclepomedev.blenderprobeforpycharm.run.app

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings

class BlenderRunningStateTest : BaseBlenderTest() {

    fun testFactoryStartupFlagIncludedWhenEnabled() {
        BlenderSettings.getInstance(project).state.useFactoryStartup = true

        val params = BlenderRunningState.buildParameters(
            useFactoryStartup = BlenderSettings.getInstance(project).state.useFactoryStartup,
            scriptPath = "/tmp/probe_server.py"
        )

        assertTrue("--factory-startup should be present when the setting is enabled", "--factory-startup" in params)
    }

    fun testFactoryStartupFlagOmittedWhenDisabled() {
        BlenderSettings.getInstance(project).state.useFactoryStartup = false

        val params = BlenderRunningState.buildParameters(
            useFactoryStartup = BlenderSettings.getInstance(project).state.useFactoryStartup,
            scriptPath = "/tmp/probe_server.py"
        )

        assertFalse("--factory-startup should be absent when the setting is disabled", "--factory-startup" in params)
    }
}
