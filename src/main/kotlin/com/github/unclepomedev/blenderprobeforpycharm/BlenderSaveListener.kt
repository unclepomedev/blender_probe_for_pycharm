package com.github.unclepomedev.blenderprobeforpycharm

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderAutoReloadService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent

/**
 * Listener for file save events to trigger automatic reload of the Blender addon.
 * When Python files within the project are modified and saved, this listener schedules a reload.
 */
class BlenderSaveListener(private val project: Project) : BulkFileListener {

    /**
     * Called after files have been processed.
     * Checks if any Python files in the project were modified and schedules a reload.
     *
     * @param events The list of file events.
     */
    override fun after(events: MutableList<out VFileEvent>) {
        val projectPath = project.basePath ?: return

        val hasPythonChanges = events.any { event ->
            if (event !is VFileContentChangeEvent) return@any false
            val file = event.file
            file.extension == "py" && file.path.startsWith(projectPath)
        }

        if (hasPythonChanges) {
            project.service<BlenderAutoReloadService>().scheduleReload()
        }
    }
}