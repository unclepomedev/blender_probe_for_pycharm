package com.github.unclepomedev.blenderprobeforpycharm

import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class BaseBlenderTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            val settings = BlenderSettings.getInstance(project)
            settings.loadState(BlenderSettings.State())
        } catch (e: Exception) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
}