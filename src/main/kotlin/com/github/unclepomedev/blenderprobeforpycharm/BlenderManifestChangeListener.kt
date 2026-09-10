package com.github.unclepomedev.blenderprobeforpycharm

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderAddonDetectionService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * Listens for file events on `blender_manifest.toml` files to invalidate cached add-on detection.
 * Invalidates only when files named `blender_manifest.toml` are created, deleted, renamed, or
 * moved.
 */
class BlenderManifestChangeListener(private val project: Project) : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        val hasManifestChanges = events.any { isRelevantManifestEvent(it) }
        if (hasManifestChanges) {
            project.service<BlenderAddonDetectionService>().invalidateCache()
        }
    }

    private fun isRelevantManifestEvent(event: VFileEvent): Boolean {
        val manifestName = BlenderAddonDetectionService.MANIFEST_FILE_NAME
        return when (event) {
            is VFileCreateEvent -> event.childName == manifestName
            is VFileDeleteEvent -> event.file.name == manifestName
            is VFileMoveEvent -> event.file.name == manifestName
            is VFilePropertyChangeEvent -> {
                event.propertyName == VirtualFile.PROP_NAME &&
                    (event.oldValue == manifestName || event.newValue == manifestName)
            }
            else -> false
        }
    }
}
