package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Project-level service that detects Blender add-on information and caches the result. Caches
 * detection across multiple queries and ensures thread-safe access and notification reporting.
 */
@Service(Service.Level.PROJECT)
class BlenderAddonDetectionService(project: Project) {

    private val lock = Any()
    private val detector = BlenderAddonDetector(project)
    private val reporter = BlenderAddonDetectionReporter(project)
    private var cachedResult: AddonDetectionResult? = null
    private var generation: Long = 0L

    internal sealed class DetectionNotificationState {
        data class Ambiguous(
            val chosenManifestPath: String,
            val sourceRoot: String?,
            val rejectedCount: Int,
        ) : DetectionNotificationState()

        data class NoManifest(
            val moduleName: String,
            val sourceRoot: String,
        ) : DetectionNotificationState()
    }

    /** Returns the cached detection result or computes a new one if not cached. */
    fun getDetectionResult(): AddonDetectionResult {
        while (true) {
            val startGeneration =
                synchronized(lock) {
                    cachedResult?.let {
                        return it
                    }
                    generation
                }

            // Execute read action outside monitor lock to avoid deadlock with write actions
            val computed =
                ApplicationManager.getApplication().runReadAction<AddonDetectionResult> {
                    detector.detect()
                }

            synchronized(lock) {
                if (generation == startGeneration) {
                    reporter.report(computed)
                    cachedResult = computed
                    return computed
                }
                cachedResult?.let {
                    return it
                }
            }
        }
    }

    /**
     * Invalidates the cached detection result. The last notification state is retained so that
     * subsequent detection does not re-notify unless the detection outcome has actually changed.
     */
    fun invalidateCache() {
        synchronized(lock) {
            generation++
            cachedResult = null
        }
    }

    /** Detects the Python module name for the Blender addon. */
    fun getAddonModuleName(): String = getDetectionResult().moduleName

    /** Locates the source root directory of the Blender addon. */
    fun getAddonSourceRoot(): String? = getDetectionResult().sourceRoot

    /** Finds the primary manifest entry file for the addon, or null if none found. */
    fun findAddonEntryFile(): VirtualFile? =
        ApplicationManager.getApplication().runReadAction<VirtualFile?> {
            detector.findPrimaryManifest()
        }

    internal fun getLastNotificationState(): DetectionNotificationState? =
        synchronized(lock) {
            reporter.getLastNotificationState()
        }

    companion object {
        const val MANIFEST_FILE_NAME = BlenderAddonDetector.MANIFEST_FILE_NAME

        fun getInstance(project: Project): BlenderAddonDetectionService =
            project.getService(BlenderAddonDetectionService::class.java)
    }
}
