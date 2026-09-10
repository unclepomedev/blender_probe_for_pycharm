package com.github.unclepomedev.blenderprobeforpycharm.services

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderAddonDetectionService.DetectionNotificationState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Responsible for logging and notifying the user about add-on detection results, with idempotent
 * suppression of repeated notifications.
 */
internal class BlenderAddonDetectionReporter(private val project: Project) {

    private var lastNotificationState: DetectionNotificationState? = null

    fun report(result: AddonDetectionResult) {
        logDetection(result)
        notifyIfNeeded(result)
    }

    fun getLastNotificationState(): DetectionNotificationState? = lastNotificationState

    private fun logDetection(result: AddonDetectionResult) {
        if (result.invalidOverridePath != null) {
            LOG.warn(
                "Configured manifest path override is missing or invalid: ${result.invalidOverridePath}"
            )
            return
        }

        if (result.manifestPath != null) {
            LOG.info(
                "Detected Blender add-on manifest: ${result.manifestPath}, source root: ${result.sourceRoot}"
            )
        }

        if (result.isAmbiguous) {
            val rejectedPaths = result.rejectedCandidates.joinToString { it.path }
            LOG.warn(
                "Multiple blender_manifest.toml files found. Chosen: ${result.manifestPath}. " +
                    "Rejected ${result.rejectedCandidates.size} candidate(s): $rejectedPaths"
            )
        }
    }

    private fun notifyIfNeeded(result: AddonDetectionResult) {
        when {
            result.invalidOverridePath != null -> notifyInvalidOverride(result)
            result.isAmbiguous -> notifyAmbiguous(result)
            result.manifestPath == null -> notifyNoManifest(result)
            else -> lastNotificationState = null
        }
    }

    private fun notifyInvalidOverride(result: AddonDetectionResult) {
        val configuredPath = result.invalidOverridePath ?: return
        val currentState = DetectionNotificationState.InvalidOverride(configuredPath)
        if (lastNotificationState == currentState) return

        lastNotificationState = currentState
        val message =
            "The configured manifest path '$configuredPath' is missing or is not a valid blender_manifest.toml file. " +
                "Auto-detection was not performed."
        showNotification("Invalid Manifest Override", message, NotificationType.WARNING)
    }

    private fun notifyAmbiguous(result: AddonDetectionResult) {
        val currentState =
            DetectionNotificationState.Ambiguous(
                chosenManifestPath = result.manifestPath!!,
                sourceRoot = result.sourceRoot,
                rejectedCount = result.rejectedCandidates.size,
            )
        if (lastNotificationState == currentState) return

        lastNotificationState = currentState
        val message = buildAmbiguousMessage(result)
        showNotification("Ambiguous Add-on Detection", message, NotificationType.WARNING)
    }

    private fun notifyNoManifest(result: AddonDetectionResult) {
        val fallbackRoot = project.basePath ?: ""
        val currentState =
            DetectionNotificationState.NoManifest(
                moduleName = result.moduleName,
                sourceRoot = fallbackRoot,
            )
        if (lastNotificationState == currentState) return

        lastNotificationState = currentState
        val message =
            "No blender_manifest.toml found in project. " +
                "Add-on module name and root were derived from the project instead: " +
                "module name '${result.moduleName}', root '$fallbackRoot'."
        showNotification("No Add-on Manifest Found", message, NotificationType.WARNING)
    }

    private fun buildAmbiguousMessage(result: AddonDetectionResult): String = buildString {
        append("Multiple add-on manifests found. Using chosen manifest: ${result.manifestPath}")
        if (result.sourceRoot != null) {
            append("\nDerived source root: ${result.sourceRoot}")
        }
        append(
            "\nRejected ${result.rejectedCandidates.size} other candidate(s). See IDE log for details."
        )
    }

    private fun showNotification(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification(title, content, type)
            ?.notify(project)
    }

    companion object {
        private val LOG = Logger.getInstance(BlenderAddonDetectionReporter::class.java)
        private const val NOTIFICATION_GROUP_ID = "Blender Probe Notification Group"
    }
}
