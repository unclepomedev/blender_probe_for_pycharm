package com.github.unclepomedev.blenderprobeforpycharm.services

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.BlenderManifestChangeListener
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BlenderAddonDetectionServiceTest : BaseBlenderTest() {

    fun testDetectAddonModuleName_Manifest() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val addonDir = baseDir.createChildDirectory(this, "my_awesome_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
            addonDir.createChildData(this, "__init__.py")
        }

        val service = BlenderAddonDetectionService.getInstance(project)
        val detectedName = service.getAddonModuleName()
        assertEquals("my_awesome_addon", detectedName)
    }

    fun testDetectAddonModuleName_Nested() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val srcDir = baseDir.createChildDirectory(this, "src")
            val addonDir = srcDir.createChildDirectory(this, "nested_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
        }

        val service = BlenderAddonDetectionService.getInstance(project)
        val detectedName = service.getAddonModuleName()
        assertEquals("nested_addon", detectedName)

        val sourceRoot = service.getAddonSourceRoot()
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

        val service = BlenderAddonDetectionService.getInstance(project)
        val detectedName = service.getAddonModuleName()
        assertEquals("savepoints", detectedName)

        val sourceRoot = service.getAddonSourceRoot()
        assertNotNull(sourceRoot)
        assertTrue(
            "Source root should be the parent of 'savepoints'",
            sourceRoot!!.endsWith("/project_root"),
        )
    }

    fun testDetectAddonModuleName_Fallback() {
        val service = BlenderAddonDetectionService.getInstance(project)
        val expected = BlenderProbeUtils.normalizeModuleName(project.name)
        val detectedName = service.getAddonModuleName()
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

        val service = BlenderAddonDetectionService.getInstance(project)
        val result = service.getDetectionResult()
        assertFalse(result.rejectedCandidates.any { it.path.contains("/tests/") })
        assertFalse(result.isAmbiguous)
        assertEquals("real_addon", service.getAddonModuleName())
        assertEquals("real_addon", result.moduleName)
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

        val service = BlenderAddonDetectionService.getInstance(project)
        val result = service.getDetectionResult()
        assertEquals("actual_addon", result.moduleName)
        assertEquals(
            baseDir.findFileByRelativePath("actual_addon/blender_manifest.toml")?.path,
            result.manifestPath,
        )
        assertEquals(baseDir.path, result.sourceRoot)
        assertEquals(0, result.rejectedCandidates.size)
        // Previous notification state should be cleared on unambiguous success
        assertNull(service.getLastNotificationState())
    }

    fun testMultipleValidCandidatesDeterministicSelection() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val shallowB = baseDir.createChildDirectory(this, "b_shallow")
            shallowB.createChildData(this, "blender_manifest.toml")

            val shallowA = baseDir.createChildDirectory(this, "a_shallow")
            shallowA.createChildData(this, "blender_manifest.toml")

            val nested = baseDir.createChildDirectory(this, "nested")
            val deep = nested.createChildDirectory(this, "deep")
            deep.createChildData(this, "blender_manifest.toml")
        }

        val service = BlenderAddonDetectionService.getInstance(project)
        val result = service.getDetectionResult()
        assertEquals("a_shallow", result.moduleName)
        val expectedManifest = baseDir.findFileByRelativePath("a_shallow/blender_manifest.toml")
        assertEquals(expectedManifest?.path, result.manifestPath)
        assertEquals(baseDir.path, result.sourceRoot)
        assertEquals(2, result.rejectedCandidates.size)
        assertEquals(listOf("b_shallow", "deep"), result.rejectedCandidates.map { it.parent.name })
        assertTrue(result.isAmbiguous)

        val state = service.getLastNotificationState()
        assertNotNull(state)
        assertTrue(state is BlenderAddonDetectionService.DetectionNotificationState.Ambiguous)
        val ambiguousState =
            state as BlenderAddonDetectionService.DetectionNotificationState.Ambiguous
        assertEquals(expectedManifest?.path, ambiguousState.chosenManifestPath)
        assertEquals(baseDir.path, ambiguousState.sourceRoot)
        assertEquals(2, ambiguousState.rejectedCount)
    }

    fun testNoManifestAtAll() {
        val service = BlenderAddonDetectionService.getInstance(project)
        val result = service.getDetectionResult()
        assertNull(result.manifestPath)
        assertEquals(BlenderProbeUtils.normalizeModuleName(project.name), result.moduleName)
        assertNull(result.sourceRoot)
        assertEquals(emptyList<Any>(), result.rejectedCandidates)
        assertFalse(result.isAmbiguous)

        val stateAfterFirst = service.getLastNotificationState()
        assertNotNull(stateAfterFirst)
        assertTrue(
            stateAfterFirst is BlenderAddonDetectionService.DetectionNotificationState.NoManifest
        )
        assertEquals(
            result.moduleName,
            (stateAfterFirst as BlenderAddonDetectionService.DetectionNotificationState.NoManifest)
                .moduleName,
        )

        // Repeated call should preserve same state (idempotent notification suppression)
        service.invalidateCache()
        service.getDetectionResult()
        val stateAfterSecond = service.getLastNotificationState()
        assertEquals(stateAfterFirst, stateAfterSecond)

        assertEquals(
            BlenderProbeUtils.normalizeModuleName(project.name),
            service.getAddonModuleName(),
        )
        assertNull(service.getAddonSourceRoot())
        assertNull(service.findAddonEntryFile())
    }

    fun testCachingBehavior() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!

        WriteAction.run<Exception> {
            val addonDir = baseDir.createChildDirectory(this, "cache_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
        }

        val service = BlenderAddonDetectionService.getInstance(project)
        val firstResult = service.getDetectionResult()
        val secondResult = service.getDetectionResult()

        // Same object instance returned by cache
        assertSame(firstResult, secondResult)

        // Sub-accessors use cached result without re-scanning
        assertEquals(firstResult.moduleName, service.getAddonModuleName())
        assertEquals(firstResult.sourceRoot, service.getAddonSourceRoot())
    }

    fun testVfsInvalidationOnManifestCreateAndDelete() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)

        // Initial state: fallback (no manifest)
        val initialResult = service.getDetectionResult()
        assertEquals(BlenderProbeUtils.normalizeModuleName(project.name), initialResult.moduleName)

        // Create manifest file
        var manifestFile: VirtualFile? = null
        WriteAction.run<Exception> {
            val addonDir = baseDir.createChildDirectory(this, "dynamic_addon")
            manifestFile = addonDir.createChildData(this, "blender_manifest.toml")
        }

        // Cache should be automatically invalidated via VFS listener
        val updatedResult = service.getDetectionResult()
        assertEquals("dynamic_addon", updatedResult.moduleName)
        assertEquals(manifestFile?.path, updatedResult.manifestPath)

        // Delete manifest file
        WriteAction.run<Exception> {
            manifestFile!!.delete(this)
        }

        // Cache should be invalidated again
        val afterDeleteResult = service.getDetectionResult()
        assertEquals(
            BlenderProbeUtils.normalizeModuleName(project.name),
            afterDeleteResult.moduleName,
        )
        assertNull(afterDeleteResult.manifestPath)
    }

    fun testVfsInvalidationOnManifestRename() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)

        var manifestFile: VirtualFile? = null
        WriteAction.run<Exception> {
            val addonDir = baseDir.createChildDirectory(this, "renamed_addon")
            manifestFile = addonDir.createChildData(this, "other.toml")
        }

        // Initial detection does not see other.toml
        assertEquals(
            BlenderProbeUtils.normalizeModuleName(project.name),
            service.getAddonModuleName(),
        )

        // Rename to blender_manifest.toml
        WriteAction.run<Exception> {
            manifestFile!!.rename(this, "blender_manifest.toml")
        }

        // Should invalidate cache and detect renamed_addon
        assertEquals("renamed_addon", service.getAddonModuleName())

        // Rename away from blender_manifest.toml
        WriteAction.run<Exception> {
            manifestFile!!.rename(this, "blender_manifest.toml.bak")
        }

        // Should invalidate cache and fall back
        assertEquals(
            BlenderProbeUtils.normalizeModuleName(project.name),
            service.getAddonModuleName(),
        )
    }

    fun testConcurrentCacheMissAndInvalidationDoesNotDeadlock() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)

        val iterations = 30
        for (i in 0 until iterations) {
            service.invalidateCache()

            val executor = Executors.newSingleThreadExecutor()
            try {
                // Background thread reads detection result concurrently
                val detectionFuture =
                    executor.submit(
                        Callable {
                            service.getDetectionResult()
                        }
                    )

                // EDT performs VFS write action triggering manifest listener invalidation
                WriteAction.run<Exception> {
                    val dir = baseDir.createChildDirectory(this, "concurrent_addon_$i")
                    val file = dir.createChildData(this, "blender_manifest.toml")
                    file.delete(this)
                    dir.delete(this)
                }

                // Detection on background thread must complete without deadlock
                val detectionResult = detectionFuture.get(5, TimeUnit.SECONDS)
                assertNotNull(detectionResult)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    fun testIsAncestorOrSelfSegmentAware() {
        val listener = BlenderManifestChangeListener(project)

        // Exact match
        assertTrue(listener.isAncestorOrSelf("/foo/bar", "/foo/bar"))
        assertTrue(listener.isAncestorOrSelf("/foo/bar/", "/foo/bar"))
        assertTrue(listener.isAncestorOrSelf("/foo/bar", "/foo/bar/"))

        // Child path
        assertTrue(listener.isAncestorOrSelf("/foo/bar", "/foo/bar/blender_manifest.toml"))
        assertTrue(listener.isAncestorOrSelf("/foo/bar", "/foo/bar/sub/blender_manifest.toml"))

        // Segment-aware: /foo/bar_baz must NOT be treated as under /foo/bar
        assertFalse(listener.isAncestorOrSelf("/foo/bar", "/foo/bar_baz"))
        assertFalse(listener.isAncestorOrSelf("/foo/bar", "/foo/bar_baz/blender_manifest.toml"))
        assertFalse(listener.isAncestorOrSelf("/foo/bar_baz", "/foo/bar/blender_manifest.toml"))
    }

    fun testVfsInvalidationOnDirectoryRenameAboveManifest() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)

        var parentDir: VirtualFile? = null
        WriteAction.run<Exception> {
            parentDir = baseDir.createChildDirectory(this, "initial_dir")
            parentDir.createChildData(this, "blender_manifest.toml")
        }

        // Cache initial result
        val firstResult = service.getDetectionResult()
        assertEquals("initial_dir", firstResult.moduleName)
        assertSame(firstResult, service.getDetectionResult())

        // Rename parent directory
        WriteAction.run<Exception> {
            parentDir!!.rename(this, "renamed_dir")
        }

        // Cache should be invalidated because parent directory changed
        val secondResult = service.getDetectionResult()
        assertNotSame(firstResult, secondResult)
        assertEquals("renamed_dir", secondResult.moduleName)
    }

    fun testVfsInvalidationOnDirectoryDeleteAboveManifest() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)

        var parentDir: VirtualFile? = null
        WriteAction.run<Exception> {
            parentDir = baseDir.createChildDirectory(this, "to_delete_dir")
            parentDir.createChildData(this, "blender_manifest.toml")
        }

        val firstResult = service.getDetectionResult()
        assertEquals("to_delete_dir", firstResult.moduleName)

        // Delete parent directory
        WriteAction.run<Exception> {
            parentDir!!.delete(this)
        }

        // Cache should be invalidated
        val secondResult = service.getDetectionResult()
        assertNotSame(firstResult, secondResult)
        assertEquals(BlenderProbeUtils.normalizeModuleName(project.name), secondResult.moduleName)
        assertNull(secondResult.manifestPath)
    }

    fun testVfsDirectoryEventElsewhereDoesNotInvalidateCache() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)

        WriteAction.run<Exception> {
            val addonDir = baseDir.createChildDirectory(this, "stable_addon")
            addonDir.createChildData(this, "blender_manifest.toml")
        }

        val firstResult = service.getDetectionResult()
        assertEquals("stable_addon", firstResult.moduleName)

        // Create, rename, and delete an unrelated directory (e.g. __pycache__ or build output)
        WriteAction.run<Exception> {
            val otherDir = baseDir.createChildDirectory(this, "unrelated_dir")
            otherDir.rename(this, "unrelated_dir_renamed")
            otherDir.delete(this)
        }

        // Cache must still hold the exact same cached instance
        val secondResult = service.getDetectionResult()
        assertSame(firstResult, secondResult)
    }

    fun testManifestOverrideHonouredWhenSet() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)
        val settings = BlenderSettings.getInstance(project)

        var manifest2: VirtualFile? = null
        WriteAction.run<Exception> {
            val dir1 = baseDir.createChildDirectory(this, "addon_alpha")
            dir1.createChildData(this, "blender_manifest.toml") // manifest1

            val dir2 = baseDir.createChildDirectory(this, "addon_beta")
            manifest2 = dir2.createChildData(this, "blender_manifest.toml")
        }

        // Without override, alpha is chosen (alphabetical)
        settings.state.manifestPath = ""
        service.invalidateCache()
        val defaultResult = service.getDetectionResult()
        assertEquals("addon_alpha", defaultResult.moduleName)
        assertTrue(defaultResult.isAmbiguous)

        val targetManifest = manifest2!!
        // Set override to manifest2
        settings.state.manifestPath = targetManifest.path
        service.invalidateCache()
        val overrideResult = service.getDetectionResult()
        assertEquals("addon_beta", overrideResult.moduleName)
        assertEquals(targetManifest.path, overrideResult.manifestPath)
        assertFalse("Override should not be marked ambiguous", overrideResult.isAmbiguous)
        assertNull(overrideResult.invalidOverridePath)

        val entryFile = service.findAddonEntryFile()
        assertNotNull(entryFile)
        assertEquals(targetManifest.path, entryFile!!.path)
    }

    fun testManifestOverrideEmptyFallsBackToDetection() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)
        val settings = BlenderSettings.getInstance(project)

        var manifest: VirtualFile? = null
        WriteAction.run<Exception> {
            val dir = baseDir.createChildDirectory(this, "auto_addon")
            manifest = dir.createChildData(this, "blender_manifest.toml")
        }

        settings.state.manifestPath = "   "
        service.invalidateCache()
        val result = service.getDetectionResult()
        assertEquals("auto_addon", result.moduleName)
        assertEquals(manifest!!.path, result.manifestPath)
        assertNull(result.invalidOverridePath)
    }

    fun testManifestOverrideNonexistentPathReportsClearlyWithoutAutoDetectFallback() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)
        val settings = BlenderSettings.getInstance(project)

        WriteAction.run<Exception> {
            val dir = baseDir.createChildDirectory(this, "real_addon")
            dir.createChildData(this, "blender_manifest.toml")
        }

        val nonExistentPath = "/path/to/nonexistent/blender_manifest.toml"
        settings.state.manifestPath = nonExistentPath
        service.invalidateCache()

        val result = service.getDetectionResult()
        assertNull(result.manifestPath)
        assertEquals(nonExistentPath, result.invalidOverridePath)
        // Must NOT fall back to real_addon
        assertNotSame("real_addon", result.moduleName)
        assertEquals(BlenderProbeUtils.normalizeModuleName(project.name), result.moduleName)

        val state = service.getLastNotificationState()
        assertTrue(state is BlenderAddonDetectionService.DetectionNotificationState.InvalidOverride)
        assertEquals(
            nonExistentPath,
            (state as BlenderAddonDetectionService.DetectionNotificationState.InvalidOverride)
                .configuredPath,
        )

        assertNull(service.findAddonEntryFile())
    }

    fun testManifestOverrideNotAManifestFileReportsClearly() {
        val baseDir = myFixture.tempDirFixture.getFile(".")!!
        val service = BlenderAddonDetectionService.getInstance(project)
        val settings = BlenderSettings.getInstance(project)

        var notManifestFile: VirtualFile? = null
        WriteAction.run<Exception> {
            val dir = baseDir.createChildDirectory(this, "addon_dir")
            dir.createChildData(this, "blender_manifest.toml")
            notManifestFile = dir.createChildData(this, "other_file.txt")
        }

        val targetNotManifest = notManifestFile!!
        settings.state.manifestPath = targetNotManifest.path
        service.invalidateCache()

        val result = service.getDetectionResult()
        assertNull(result.manifestPath)
        assertEquals(targetNotManifest.path, result.invalidOverridePath)
        assertNotSame("addon_dir", result.moduleName)

        val state = service.getLastNotificationState()
        assertTrue(state is BlenderAddonDetectionService.DetectionNotificationState.InvalidOverride)
    }
}
