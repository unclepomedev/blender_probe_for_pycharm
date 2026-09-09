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

    fun testFactoryStartupDefaultsToTrue() {
        val settings = BlenderSettings.getInstance(project)
        assertTrue(
            "--factory-startup should be enabled by default",
            settings.state.useFactoryStartup,
        )
    }

    fun testFactoryStartupSettingPersists() {
        val settings = BlenderSettings.getInstance(project)
        val newState = BlenderSettings.State(useFactoryStartup = false)

        settings.loadState(newState)
        assertFalse(settings.state.useFactoryStartup)
    }

    fun testResolveBlenderPathDoesNotFailWhenBasePathDoesNotExist() {
        val settings = BlenderSettings.getInstance(project)
        settings.state.blenderPath = ""

        val basePath = project.basePath
        assertNotNull("Project basePath should not be null", basePath)
        val baseDir = java.io.File(basePath!!)
        if (baseDir.exists()) {
            baseDir.deleteRecursively()
        }
        assertFalse(
            "Project basePath should not exist to verify fallback behavior",
            baseDir.exists(),
        )

        // Ensure no exception is thrown when resolving path with non-existent basePath
        try {
            settings.resolveBlenderPath()
        } catch (e: Exception) {
            fail(
                "resolveBlenderPath should not throw an exception when basePath does not exist: ${e.message}"
            )
        }
    }
}
