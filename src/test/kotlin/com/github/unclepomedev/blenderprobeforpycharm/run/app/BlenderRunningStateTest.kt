package com.github.unclepomedev.blenderprobeforpycharm.run.app

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings

class BlenderRunningStateTest : BaseBlenderTest() {

    fun testFactoryStartupFlagIncludedWhenEnabled() {
        assertFactoryStartupFlag(enabled = true, expected = true)
    }

    fun testFactoryStartupFlagOmittedWhenDisabled() {
        assertFactoryStartupFlag(enabled = false, expected = false)
    }

    private fun assertFactoryStartupFlag(enabled: Boolean, expected: Boolean) {
        val settings = BlenderSettings.getInstance(project)
        settings.state.useFactoryStartup = enabled

        val params = BlenderRunningState.buildParameters(
            useFactoryStartup = settings.state.useFactoryStartup,
            scriptPath = "/tmp/probe_server.py"
        )

        assertEquals(
            "--factory-startup presence should match the setting (enabled=$enabled)",
            expected,
            "--factory-startup" in params
        )
    }
}
