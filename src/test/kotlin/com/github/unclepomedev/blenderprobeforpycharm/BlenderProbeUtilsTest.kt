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
        assertTrue(
            "Source root should be the parent of 'savepoints'",
            sourceRoot!!.endsWith("/project_root"),
        )
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

    fun testManifestDeepInsideExcludedDirectory() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val venvDir = baseDir.createChildDirectory(this, "venv")
            val libDir = venvDir.createChildDirectory(this, "lib")
            val pythonDir = libDir.createChildDirectory(this, "python3.11")
            val sitePackages = pythonDir.createChildDirectory(this, "site-packages")
            val pkgDir = sitePackages.createChildDirectory(this, "some_pkg")
            pkgDir.createChildData(this, "blender_manifest.toml")

            val addonDir = baseDir.createChildDirectory(this, "actual_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
        }

        val result = BlenderProbeUtils.detectAddon(project)
        assertEquals("actual_addon", result.moduleName)
        assertEquals(baseDir.findFileByRelativePath("actual_addon/blender_manifest.toml")?.path, result.manifestPath)
        assertEquals(baseDir.path, result.sourceRoot)
        assertEquals(0, result.rejectedCandidates.size)
    }

    fun testMultipleValidCandidatesDeterministicSelection() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            // Shallow candidate B (depth: baseDir / b_shallow / blender_manifest.toml)
            val shallowB = baseDir.createChildDirectory(this, "b_shallow")
            shallowB.createChildData(this, "blender_manifest.toml")

            // Shallow candidate A (depth: baseDir / a_shallow / blender_manifest.toml)
            val shallowA = baseDir.createChildDirectory(this, "a_shallow")
            shallowA.createChildData(this, "blender_manifest.toml")

            // Deeper candidate (depth: baseDir / nested / deep / blender_manifest.toml)
            val nested = baseDir.createChildDirectory(this, "nested")
            val deep = nested.createChildDirectory(this, "deep")
            deep.createChildData(this, "blender_manifest.toml")
        }

        val result = BlenderProbeUtils.detectAddon(project)
        // Between shallowA and shallowB, shallowA comes first lexicographically.
        // Both are shallower than deep.
        assertEquals("a_shallow", result.moduleName)
        val expectedManifest = baseDir.findFileByRelativePath("a_shallow/blender_manifest.toml")
        assertEquals(expectedManifest?.path, result.manifestPath)
        assertEquals(baseDir.path, result.sourceRoot)
        assertEquals(2, result.rejectedCandidates.size)
        assertEquals(listOf("b_shallow", "deep"), result.rejectedCandidates.map { it.parent.name })
        assertTrue(result.isAmbiguous)
    }

    fun testNoManifestAtAll() {
        val result = BlenderProbeUtils.detectAddon(project)
        assertNull(result.manifestPath)
        assertEquals(BlenderProbeUtils.normalizeModuleName(project.name), result.moduleName)
        assertNull(result.sourceRoot)
        assertEquals(emptyList<Any>(), result.rejectedCandidates)
        assertFalse(result.isAmbiguous)

        assertEquals(BlenderProbeUtils.normalizeModuleName(project.name), BlenderProbeUtils.detectAddonModuleName(project))
        assertNull(BlenderProbeUtils.getAddonSourceRoot(project))
        assertNull(BlenderProbeUtils.findAddonEntryFile(project))
    }
}
