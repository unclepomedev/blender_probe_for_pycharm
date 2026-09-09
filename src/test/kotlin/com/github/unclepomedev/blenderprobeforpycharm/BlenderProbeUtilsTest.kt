package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.openapi.application.WriteAction

class BlenderProbeUtilsTest : BaseBlenderTest() {

    fun testNormalizeModuleName() {
        assertEquals("my_awesome_addon", BlenderProbeUtils.normalizeModuleName("my-awesome-addon"))
        assertEquals("addon_name", BlenderProbeUtils.normalizeModuleName("Addon Name"))
        assertEquals("project_123", BlenderProbeUtils.normalizeModuleName("Project 123"))
    }

    fun testSortCandidates() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        var shallowA: com.intellij.openapi.vfs.VirtualFile? = null
        var shallowB: com.intellij.openapi.vfs.VirtualFile? = null
        var deep: com.intellij.openapi.vfs.VirtualFile? = null

        WriteAction.run<Exception> {
            val dirB = baseDir.createChildDirectory(this, "b_shallow")
            shallowB = dirB.createChildData(this, "blender_manifest.toml")

            val dirA = baseDir.createChildDirectory(this, "a_shallow")
            shallowA = dirA.createChildData(this, "blender_manifest.toml")

            val dirNested = baseDir.createChildDirectory(this, "nested")
            val dirDeep = dirNested.createChildDirectory(this, "deep")
            deep = dirDeep.createChildData(this, "blender_manifest.toml")
        }

        val sorted = BlenderProbeUtils.sortCandidates(listOf(deep!!, shallowB!!, shallowA!!))
        assertEquals(listOf(shallowA, shallowB, deep), sorted)
    }

    fun testIsUnderExcludedDirectoryStopsAtContentRoot() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val excludedAncestor = baseDir.createChildDirectory(this, "build")
            val contentRoot = excludedAncestor.createChildDirectory(this, "my_project")
            val addonDir = contentRoot.createChildDirectory(this, "addon")
            val manifestFile = addonDir.createChildData(this, "blender_manifest.toml")

            assertTrue(BlenderProbeUtils.isUnderExcludedDirectory(manifestFile, null))
            assertFalse(BlenderProbeUtils.isUnderExcludedDirectory(manifestFile, contentRoot))

            val innerExcluded = contentRoot.createChildDirectory(this, "tests")
            val innerManifest = innerExcluded.createChildData(this, "blender_manifest.toml")
            assertTrue(BlenderProbeUtils.isUnderExcludedDirectory(innerManifest, contentRoot))
        }
    }
}
