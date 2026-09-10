package com.github.unclepomedev.blenderprobeforpycharm

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderAddonDetectionService
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class BaseBlenderTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            val baseDir = myFixture.tempDirFixture.getFile(".")
            if (baseDir != null && baseDir.isValid) {
                WriteAction.run<Exception> {
                    baseDir.children.forEach { it.delete(this) }
                }
            }
            BlenderAddonDetectionService.getInstance(project).invalidateCache()
            val settings = BlenderSettings.getInstance(project)
            settings.loadState(BlenderSettings.State())
        } catch (e: Exception) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }
}
