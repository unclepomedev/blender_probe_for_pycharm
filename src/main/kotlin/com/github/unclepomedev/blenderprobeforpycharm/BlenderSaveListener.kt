package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent

class BlenderSaveListener(private val project: Project) : BulkFileListener {

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