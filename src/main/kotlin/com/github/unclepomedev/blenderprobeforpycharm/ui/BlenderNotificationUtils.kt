package com.github.unclepomedev.blenderprobeforpycharm.ui

import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettingsConfigurable
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

/** Utility functions for displaying plugin notifications to the user. */
object BlenderNotificationUtils {
    const val NOTIFICATION_GROUP_ID = "Blender Probe Notification Group"

    /** Displays a notification with an action to open the plugin's settings page. */
    fun showNotificationWithSettings(
        project: Project,
        title: String,
        content: String,
        type: NotificationType = NotificationType.ERROR,
    ) {
        val notification =
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                ?.createNotification(title, content, type) ?: return

        notification.addAction(
            NotificationAction.create("Open settings") { _, notif ->
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, BlenderSettingsConfigurable::class.java)
                notif.expire()
            }
        )
        notification.notify(project)
    }
}
