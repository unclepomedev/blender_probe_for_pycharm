package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/** Handles user notifications related to Blender stub generation. */
internal class BlenderStubNotifier(private val project: Project) {

    fun notifySuccess() {
        notify("Success", "Stubs generated in .blender_stubs", NotificationType.INFORMATION)
    }

    fun notifyCancelled() {
        notify(
            "Generation Cancelled",
            "Blender stub generation was cancelled.",
            NotificationType.INFORMATION,
        )
    }

    fun notifyFailed(errorMessage: String) {
        notify(
            "Generation Failed",
            "Failed to generate stubs: $errorMessage",
            NotificationType.ERROR,
        )
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification(title, content, type)
            ?.notify(project)
    }

    companion object {
        private const val NOTIFICATION_GROUP_ID = "Blender Probe Notification Group"
    }
}
