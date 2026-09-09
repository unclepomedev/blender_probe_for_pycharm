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
        settings.state.entries.clear()
        settings.state.currentEntryName = ""

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

    fun testMultipleEntriesAndSwitching() {
        val settings = BlenderSettings.getInstance(project)
        settings.loadState(BlenderSettings.State())

        val entry1 = BlenderEntry("Blender 4.5", "/usr/local/bin/blender-4.5")
        val entry2 = BlenderEntry("Blender 5.2", "/usr/local/bin/blender-5.2")
        settings.state.entries.add(entry1)
        settings.state.entries.add(entry2)

        // Select entry1
        settings.setActiveEntry("Blender 4.5")
        assertEquals("Blender 4.5", settings.state.currentEntryName)
        assertEquals("/usr/local/bin/blender-4.5", settings.resolveBlenderPath())

        // Switch to entry2
        settings.setActiveEntry("Blender 5.2")
        assertEquals("Blender 5.2", settings.state.currentEntryName)
        assertEquals("/usr/local/bin/blender-5.2", settings.resolveBlenderPath())
    }

    fun testBlankCurrentEntryNameReturnsNullSelectedEntry() {
        val settings = BlenderSettings.getInstance(project)
        settings.loadState(BlenderSettings.State())

        val entry = BlenderEntry("Blender 5.2", "/usr/local/bin/blender-5.2")
        settings.state.entries.add(entry)
        settings.state.currentEntryName = ""

        assertNull("getSelectedEntry should return null when currentEntryName is blank", settings.getSelectedEntry())

        // Also verify setActiveEntry with non-matching name clears currentEntryName and blenderPath
        settings.state.currentEntryName = "Blender 5.2"
        settings.state.blenderPath = "/usr/local/bin/blender-5.2"
        settings.setActiveEntry("NonExistent")
        assertEquals("", settings.state.currentEntryName)
        assertEquals("", settings.state.blenderPath)
    }

    fun testMigrationFromLegacyBlenderPath() {
        val settings = BlenderSettings.getInstance(project)
        val legacyState =
            BlenderSettings.State(blenderPath = "/Applications/Blender.app/Contents/MacOS/Blender")

        settings.loadState(legacyState)

        assertEquals(1, settings.state.entries.size)
        val migratedEntry = settings.state.entries.first()
        assertEquals("Blender", migratedEntry.name)
        assertEquals("/Applications/Blender.app/Contents/MacOS/Blender", migratedEntry.path)
        assertEquals("Blender", settings.state.currentEntryName)
        assertEquals(
            "/Applications/Blender.app/Contents/MacOS/Blender",
            settings.resolveBlenderPath(),
        )
    }

    fun testConfigurableUIStateAndPersistence() {
        val settings = BlenderSettings.getInstance(project)
        settings.loadState(BlenderSettings.State())

        val configurable = BlenderSettingsConfigurable(project)
        val panel = configurable.createComponent()
        assertNotNull(panel)

        // Check initial state
        assertFalse(configurable.isModified)

        // Modify settings through settings object and test reset
        settings.state.entries.add(BlenderEntry("Blender Test", "/dummy/path"))
        settings.setActiveEntry("Blender Test")
        assertTrue(configurable.isModified)

        configurable.reset()
        assertFalse(configurable.isModified)

        // Apply saves correctly
        configurable.apply()
        assertEquals("Blender Test", settings.state.currentEntryName)
        assertEquals("/dummy/path", settings.resolveBlenderPath())

        configurable.disposeUIResources()
    }
}
