package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element

class BlenderTestRunConfigurationTest : BasePlatformTestCase() {

    fun testConfigurationTypeAndFactory() {
        val configType = BlenderTestConfigurationType()

        assertEquals(BlenderTestConfigurationType.ID, configType.id)
        assertEquals("Blender Test", configType.displayName)

        val factory = configType.configurationFactories.first()
        assertTrue(factory is BlenderTestConfigurationFactory)
        assertEquals("BlenderTestConfigurationFactory", factory.id)

        val config = factory.createTemplateConfiguration(project)
        assertTrue(config is BlenderTestRunConfiguration)
    }

    fun testConfigurationOptionsPersistence() {
        val configType = BlenderTestConfigurationType()
        val factory = configType.configurationFactories.first()
        val config = factory.createTemplateConfiguration(project) as BlenderTestRunConfiguration

        assertEquals("", config.testDir)

        val expectedPath = "/path/to/my/tests"
        config.testDir = expectedPath
        assertEquals(expectedPath, config.testDir)

        val element = Element("configuration")
        config.writeExternal(element)

        val newConfig = factory.createTemplateConfiguration(project) as BlenderTestRunConfiguration
        newConfig.readExternal(element)

        assertEquals("Persistence logic should preserve testDir", expectedPath, newConfig.testDir)
    }

    fun testCheckConfiguration_ValidateBlenderPath() {
        val config = createTemplateConfig()

        BlenderSettings.getInstance(project).state.blenderPath = ""
        config.testDir = "/some/test/dir"

        try {
            config.checkConfiguration()
            fail("Should throw RuntimeConfigurationException when Blender path is missing")
        } catch (e: RuntimeConfigurationException) {
            assertEquals("Blender executable path is not set.", e.localizedMessage)
        }
    }

    fun testCheckConfiguration_ValidateTestDir() {
        val config = createTemplateConfig()

        BlenderSettings.getInstance(project).state.blenderPath = "/path/to/blender"
        config.testDir = ""

        try {
            config.checkConfiguration()
            fail("Should throw RuntimeConfigurationException when Test directory is missing")
        } catch (e: RuntimeConfigurationException) {
            assertEquals("Test directory is not specified.", e.localizedMessage)
        }
    }

    fun testCheckConfiguration_Valid() {
        val config = createTemplateConfig()

        BlenderSettings.getInstance(project).state.blenderPath = "/path/to/blender"
        config.testDir = "/some/test/dir"

        try {
            config.checkConfiguration()
        } catch (e: Exception) {
            fail("Should not throw exception when configuration is valid. Error: ${e.message}")
        }
    }

    private fun createTemplateConfig(): BlenderTestRunConfiguration {
        val configType = BlenderTestConfigurationType()
        val factory = configType.configurationFactories.first()
        return factory.createTemplateConfiguration(project) as BlenderTestRunConfiguration
    }
}