package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.openapi.application.WriteAction

class BlenderProbeUtilsTest : BaseBlenderTest() {

    fun testDetectAddonModuleName_Manifest() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val addonDir = baseDir.createChildDirectory(this, "my_awesome_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
            addonDir.createChildData(this, "__init__.py")
        }

        val detectedName = BlenderProbeUtils.detectAddonModuleName(project)
        assertEquals("my_awesome_addon", detectedName)
    }

    fun testDetectAddonModuleName_Nested() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val srcDir = baseDir.createChildDirectory(this, "src")
            val addonDir = srcDir.createChildDirectory(this, "nested_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
        }

        val detectedName = BlenderProbeUtils.detectAddonModuleName(project)
        assertEquals("nested_addon", detectedName)

        val sourceRoot = BlenderProbeUtils.getAddonSourceRoot(project)
        assertNotNull(sourceRoot)
        assertTrue(sourceRoot!!.endsWith("/src"))
    }

    fun testGetAddonSourceRoot_DeepNested() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val p1 = baseDir.createChildDirectory(this, "project_root")
            val p2 = p1.createChildDirectory(this, "savepoints")
            p2.createChildData(this, "blender_manifest.toml")
        }

        val detectedName = BlenderProbeUtils.detectAddonModuleName(project)
        assertEquals("savepoints", detectedName)

        val sourceRoot = BlenderProbeUtils.getAddonSourceRoot(project)
        assertNotNull(sourceRoot)
        assertTrue("Source root should be the parent of 'savepoints'", sourceRoot!!.endsWith("/project_root"))
    }

    fun testDetectAddonModuleName_Fallback() {
        val expected = BlenderProbeUtils.normalizeModuleName(project.name)
        val detectedName = BlenderProbeUtils.detectAddonModuleName(project)
        assertEquals(expected, detectedName)
    }

    fun testDetectAddonModuleName_ExcludeTests() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val testsDir = baseDir.createChildDirectory(this, "tests")
            testsDir.createChildData(this, "blender_manifest.toml")
            val addonDir = baseDir.createChildDirectory(this, "real_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
        }

        val detectedName = BlenderProbeUtils.detectAddonModuleName(project)
        assertEquals("real_addon", detectedName)
    }
}