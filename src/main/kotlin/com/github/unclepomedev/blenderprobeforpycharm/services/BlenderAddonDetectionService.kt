package com.github.unclepomedev.blenderprobeforpycharm.services

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Project-level service that detects Blender add-on information and caches the result. Caches
 * detection across multiple queries and ensures thread-safe access and notification reporting.
 */
@Service(Service.Level.PROJECT)
class BlenderAddonDetectionService(private val project: Project) {

    private val lock = Any()
    private var cachedResult: AddonDetectionResult? = null
    private var lastNotificationState: DetectionNotificationState? = null

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
        synchronized(lock) {
            cachedResult?.let {
                return it
            }

            val result = scanProject()
            cachedResult = result
            reportDetectionResult(result)
            return result
        }
    }

    /**
     * Invalidates the cached detection result. The last notification state is retained so that
     * subsequent detection does not re-notify unless the detection outcome has actually changed.
     */
    fun invalidateCache() {
        synchronized(lock) {
            cachedResult = null
        }
    }

    /** Detects the Python module name for the Blender addon. */
    fun getAddonModuleName(): String {
        return getDetectionResult().moduleName
    }

    /** Locates the source root directory of the Blender addon. */
    fun getAddonSourceRoot(): String? {
        return getDetectionResult().sourceRoot
    }

    /** Finds the primary manifest entry file for the addon, or null if none found. */
    fun findAddonEntryFile(): VirtualFile? {
        val candidates = findCandidateManifestFiles(project)
        return BlenderProbeUtils.sortCandidates(candidates).firstOrNull()
    }

    internal fun getLastNotificationState(): DetectionNotificationState? {
        synchronized(lock) {
            return lastNotificationState
        }
    }

    private fun scanProject(): AddonDetectionResult {
        val candidates = findCandidateManifestFiles(project)
        val sortedCandidates = BlenderProbeUtils.sortCandidates(candidates)

        val chosen = sortedCandidates.firstOrNull()
        val rejected = if (sortedCandidates.isNotEmpty()) sortedCandidates.drop(1) else emptyList()

        return if (chosen != null) {
            val addonDir = chosen.parent
            val moduleName = addonDir?.name ?: BlenderProbeUtils.normalizeModuleName(project.name)
            val sourceRoot = addonDir?.parent?.path
            AddonDetectionResult(
                manifestPath = chosen.path,
                moduleName = moduleName,
                sourceRoot = sourceRoot,
                rejectedCandidates = rejected,
            )
        } else {
            AddonDetectionResult(
                manifestPath = null,
                moduleName = BlenderProbeUtils.normalizeModuleName(project.name),
                sourceRoot = null,
                rejectedCandidates = emptyList(),
            )
        }
    }

    private fun reportDetectionResult(result: AddonDetectionResult) {
        if (result.manifestPath != null) {
            LOG.info(
                "Detected Blender add-on manifest: ${result.manifestPath}, source root: ${result.sourceRoot}"
            )
        }

        if (result.isAmbiguous) {
            LOG.warn(
                "Multiple blender_manifest.toml files found. Chosen: ${result.manifestPath}. " +
                    "Rejected ${result.rejectedCandidates.size} candidate(s): " +
                    result.rejectedCandidates.joinToString { it.path }
            )

            val currentState =
                DetectionNotificationState.Ambiguous(
                    chosenManifestPath = result.manifestPath!!,
                    sourceRoot = result.sourceRoot,
                    rejectedCount = result.rejectedCandidates.size,
                )
            if (lastNotificationState != currentState) {
                lastNotificationState = currentState
                val message = buildString {
                    append(
                        "Multiple add-on manifests found. Using chosen manifest: ${result.manifestPath}"
                    )
                    if (result.sourceRoot != null) {
                        append("\nDerived source root: ${result.sourceRoot}")
                    }
                    append(
                        "\nRejected ${result.rejectedCandidates.size} other candidate(s). See IDE log for details."
                    )
                }
                showNotification(
                    project,
                    "Ambiguous Add-on Detection",
                    message,
                    NotificationType.WARNING,
                )
            }
        } else if (result.manifestPath == null) {
            val fallbackRoot = project.basePath ?: ""
            val currentState =
                DetectionNotificationState.NoManifest(
                    moduleName = result.moduleName,
                    sourceRoot = fallbackRoot,
                )
            if (lastNotificationState != currentState) {
                lastNotificationState = currentState
                val message =
                    "No blender_manifest.toml found in project. " +
                        "Add-on module name and root were derived from the project instead: " +
                        "module name '${result.moduleName}', root '$fallbackRoot'."
                showNotification(
                    project,
                    "No Add-on Manifest Found",
                    message,
                    NotificationType.WARNING,
                )
            }
        } else {
            // Unambiguous successful detection: clear any previous notification state
            lastNotificationState = null
        }
    }

    private fun findCandidateManifestFiles(project: Project): List<VirtualFile> {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val candidates = mutableListOf<VirtualFile>()

        fileIndex.iterateContent { file: VirtualFile ->
            if (!file.isDirectory && file.name == MANIFEST_FILE_NAME) {
                val contentRoot = fileIndex.getContentRootForFile(file)
                if (!BlenderProbeUtils.isUnderExcludedDirectory(file, contentRoot)) {
                    candidates.add(file)
                }
            }
            true
        }
        return candidates
    }

    private fun showNotification(
        project: Project,
        title: String,
        content: String,
        type: NotificationType,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification(title, content, type)
            ?.notify(project)
    }

    companion object {
        const val MANIFEST_FILE_NAME = "blender_manifest.toml"
        private val LOG = Logger.getInstance(BlenderAddonDetectionService::class.java)
        private const val NOTIFICATION_GROUP_ID = "Blender Probe Notification Group"

        fun getInstance(project: Project): BlenderAddonDetectionService =
            project.getService(BlenderAddonDetectionService::class.java)
    }
}
