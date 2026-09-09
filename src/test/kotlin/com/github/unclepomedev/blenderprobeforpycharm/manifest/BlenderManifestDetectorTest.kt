package com.github.unclepomedev.blenderprobeforpycharm.manifest

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile

class BlenderManifestDetectorTest : BaseBlenderTest() {

    fun testDetectAddonModuleName_Manifest() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val addonDir = baseDir.createChildDirectory(this, "my_awesome_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
            addonDir.createChildData(this, "__init__.py")
        }

        val detectedName = BlenderManifestDetector.detectAddonModuleName(project)
        assertEquals("my_awesome_addon", detectedName)
    }

    fun testDetectAddonModuleName_Nested() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val srcDir = baseDir.createChildDirectory(this, "src")
            val addonDir = srcDir.createChildDirectory(this, "nested_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
        }

        val detectedName = BlenderManifestDetector.detectAddonModuleName(project)
        assertEquals("nested_addon", detectedName)

        val sourceRoot = BlenderManifestDetector.getAddonSourceRoot(project)
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

        val detectedName = BlenderManifestDetector.detectAddonModuleName(project)
        assertEquals("savepoints", detectedName)

        val sourceRoot = BlenderManifestDetector.getAddonSourceRoot(project)
        assertNotNull(sourceRoot)
        assertTrue(
            "Source root should be the parent of 'savepoints'",
            sourceRoot!!.endsWith("/project_root"),
        )
    }

    fun testDetectAddonModuleName_Fallback() {
        val expected = BlenderManifestDetector.normalizeModuleName(project.name)
        val detectedName = BlenderManifestDetector.detectAddonModuleName(project)
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

        val detectedName = BlenderManifestDetector.detectAddonModuleName(project)
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

        val result = BlenderManifestDetector.detectAddon(project)
        assertEquals("actual_addon", result.moduleName)
        assertEquals(
            baseDir.findFileByRelativePath("actual_addon/blender_manifest.toml")?.path,
            result.manifestPath,
        )
        assertEquals(baseDir.path, result.sourceRoot)
        assertEquals(0, result.rejectedCandidates.size)
    }

    fun testMultipleValidCandidatesDeterministicSelection() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val shallowB = baseDir.createChildDirectory(this, "b_shallow")
            shallowB.createChildData(this, "blender_manifest.toml")

            val shallowA = baseDir.createChildDirectory(this, "a_shallow")
            shallowA.createChildData(this, "blender_manifest.toml")

            val deepDir1 = baseDir.createChildDirectory(this, "nested1")
            val deepDir2 = deepDir1.createChildDirectory(this, "nested2")
            val deep = deepDir2.createChildDirectory(this, "deep_candidate")
            deep.createChildData(this, "blender_manifest.toml")
        }

        val result = BlenderManifestDetector.detectAddon(project)

        val expectedManifest = baseDir.findFileByRelativePath("a_shallow/blender_manifest.toml")
        assertNotNull(expectedManifest)
        assertEquals(expectedManifest!!.path, result.manifestPath)
        assertEquals("a_shallow", result.moduleName)
        assertEquals(baseDir.path, result.sourceRoot)

        assertTrue(result.isAmbiguous)
        assertTrue(result.isResolved)
        assertEquals(2, result.rejectedCandidates.size)
        assertEquals("b_shallow", result.rejectedCandidates[0].parent?.name)
        assertEquals("deep_candidate", result.rejectedCandidates[1].parent?.name)
    }

    fun testNoManifestFallback() {
        val result = BlenderManifestDetector.detectAddon(project)

        assertNull(result.manifestPath)
        assertNull(result.sourceRoot)
        assertEquals(emptyList<Any>(), result.rejectedCandidates)
        assertFalse(result.isAmbiguous)
        assertFalse(result.isResolved)

        val message = result.formatMessage()
        assertTrue(message.contains("No valid blender_manifest.toml found."))
        assertTrue(message.contains("Searched in:"))

        assertEquals(
            BlenderManifestDetector.normalizeModuleName(project.name),
            result.moduleName,
        )
        assertEquals(
            BlenderManifestDetector.normalizeModuleName(project.name),
            BlenderManifestDetector.detectAddonModuleName(project),
        )
        assertNull(BlenderManifestDetector.getAddonSourceRoot(project))
        assertNull(BlenderManifestDetector.findAddonEntryFile(project))
    }

    fun testFormatMessageWhenResolvedAndAmbiguous() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val shallowA = baseDir.createChildDirectory(this, "addon_1")
            shallowA.createChildData(this, "blender_manifest.toml")

            val shallowB = baseDir.createChildDirectory(this, "addon_2")
            shallowB.createChildData(this, "blender_manifest.toml")
        }

        val result = BlenderManifestDetector.detectAddon(project)
        assertTrue(result.isResolved)
        assertTrue(result.isAmbiguous)
        val message = result.formatMessage()
        assertTrue(message.contains("Selected manifest: ${result.manifestPath}"))
        assertTrue(message.contains("Derived source root: ${result.sourceRoot}"))
        assertTrue(message.contains("Found 2 candidate manifests in project."))
    }

    fun testFormatMessageWhenOnlyExcludedCandidatesExist() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val venv = baseDir.createChildDirectory(this, "venv")
            venv.createChildData(this, "blender_manifest.toml")
        }

        val result = BlenderManifestDetector.detectAddon(project)
        assertFalse(result.isResolved)
        val message = result.formatMessage()
        assertTrue(message.contains("No valid blender_manifest.toml found."))
        assertTrue(message.contains("Searched in:"))
        assertTrue(message.contains("Rejected candidates:"))
        assertTrue(message.contains("inside excluded directory 'venv'"))
    }

    fun testIsUnderExcludedDirectoryStopsAtContentRoot() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        var manifestFile: VirtualFile? = null
        var contentRoot: VirtualFile? = null
        var innerManifest: VirtualFile? = null

        WriteAction.run<Exception> {
            val buildDir = baseDir.createChildDirectory(this, "build")
            val projectRoot = buildDir.createChildDirectory(this, "my_project")
            contentRoot = projectRoot

            val myAddon = projectRoot.createChildDirectory(this, "my_addon")
            manifestFile = myAddon.createChildData(this, "blender_manifest.toml")

            val testsDir = projectRoot.createChildDirectory(this, "tests")
            innerManifest = testsDir.createChildData(this, "blender_manifest.toml")
        }

        val manifest = manifestFile!!
        val inner = innerManifest!!
        assertTrue(BlenderManifestDetector.isUnderExcludedDirectory(manifest, null))
        assertFalse(BlenderManifestDetector.isUnderExcludedDirectory(manifest, contentRoot))
        assertTrue(BlenderManifestDetector.isUnderExcludedDirectory(inner, contentRoot))
    }
}
