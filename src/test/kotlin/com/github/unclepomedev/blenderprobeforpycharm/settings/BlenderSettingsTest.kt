package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest

class BlenderSettingsTest : BaseBlenderTest() {

    fun testSettingsServiceInstantiation() {
        val settings = BlenderSettings.getInstance(project)
        assertNotNull("Service should be retrieved successfully", settings)
    }

    fun testStatePersistence() {
        val settings = BlenderSettings.getInstance(project)

        assertEquals("", settings.state.blenderPath)

        val newPath = "/usr/bin/blender_test"
        settings.state.blenderPath = newPath

        assertEquals(newPath, settings.state.blenderPath)
    }

    fun testLoadState() {
        val settings = BlenderSettings.getInstance(project)
        val newState = BlenderSettings.State(blenderPath = "C:\\Blender\\blender.exe")

        settings.loadState(newState)
        assertEquals("C:\\Blender\\blender.exe", settings.state.blenderPath)
    }
}