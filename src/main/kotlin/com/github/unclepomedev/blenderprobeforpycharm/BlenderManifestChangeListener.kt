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
 * Invalidates cached add-on detection on manifest file events, and on directory events only when
 * the affected path is an ancestor of the cached manifest.
 *
 * Deliberately narrow: this project churns directories constantly (`__pycache__`, stub generation,
 * build output), so invalidating on any directory event would defeat the cache. The cost is that a
 * manifest moved out of an excluded directory by moving its parent goes unnoticed until the next
 * invalidation.
 */
class BlenderManifestChangeListener(private val project: Project) : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        val detectionService = project.service<BlenderAddonDetectionService>()
        val cachedManifestPath = detectionService.getCachedManifestPath()

        val hasManifestChanges = events.any { isRelevantManifestEvent(it, cachedManifestPath) }
        if (hasManifestChanges) {
            detectionService.invalidateCache()
        }
    }

    private fun isRelevantManifestEvent(event: VFileEvent, cachedManifestPath: String?): Boolean {
        return isDirectManifestEvent(event) || isAffectingCachedManifest(event, cachedManifestPath)
    }

    private fun isDirectManifestEvent(event: VFileEvent): Boolean {
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

    private fun isAffectingCachedManifest(event: VFileEvent, cachedManifestPath: String?): Boolean {
        return cachedManifestPath != null &&
            when (event) {
                is VFileDeleteEvent -> {
                    event.file.isDirectory && isAncestorOrSelf(event.path, cachedManifestPath)
                }

                is VFileMoveEvent -> {
                    event.file.isDirectory &&
                        affectsPath(event.oldPath, event.path, cachedManifestPath)
                }

                is VFilePropertyChangeEvent -> {
                    event.propertyName == VirtualFile.PROP_NAME &&
                        event.file.isDirectory &&
                        affectsPath(event.oldPath, event.path, cachedManifestPath)
                }

                else -> false
            }
    }

    private fun affectsPath(oldPath: String, newPath: String, targetPath: String): Boolean {
        return isAncestorOrSelf(oldPath, targetPath) || isAncestorOrSelf(newPath, targetPath)
    }

    internal fun isAncestorOrSelf(ancestorPath: String, targetPath: String): Boolean {
        val normalizedAncestor = ancestorPath.trimEnd('/')
        val normalizedTarget = targetPath.trimEnd('/')

        return normalizedAncestor == normalizedTarget ||
            normalizedTarget.startsWith("$normalizedAncestor/")
    }
}
