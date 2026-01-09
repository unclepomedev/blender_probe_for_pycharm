package com.github.unclepomedev.blenderprobeforpycharm.run

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BlenderTestSettingsEditorTest : BasePlatformTestCase() {

    fun testEditorApplyAndReset() {
        val configType = BlenderTestConfigurationType()
        val factory = configType.configurationFactories.first()
        val config = factory.createTemplateConfiguration(project) as BlenderTestRunConfiguration

        val editor = BlenderTestSettingsEditor()
        try {
            val initialPath = "/initial/path"
            config.testDir = initialPath
            editor.resetFrom(config)

            val newConfig = factory.createTemplateConfiguration(project) as BlenderTestRunConfiguration

            editor.applyTo(newConfig)
            assertEquals("UI state should be applied to the configuration", initialPath, newConfig.testDir)

        } finally {
            Disposer.dispose(editor)
        }
    }
}