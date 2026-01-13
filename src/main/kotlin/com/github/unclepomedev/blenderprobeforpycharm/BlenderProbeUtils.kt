package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

object BlenderProbeUtils {
    fun detectAddonModuleName(project: Project): String {
        val targetFile = findAddonEntryFile(project)

        if (targetFile != null) {
            return targetFile.parent.name
        }
        return normalizeModuleName(project.name)
    }

    fun getAddonSourceRoot(project: Project): String? {
        val targetFile = findAddonEntryFile(project) ?: return null
        val addonDir = targetFile.parent ?: return null

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val contentRoot = fileIndex.getContentRootForFile(targetFile)

        if (contentRoot != null && addonDir == contentRoot) {
            return addonDir.path
        }
        return addonDir.parent?.path
    }

    private fun findAddonEntryFile(project: Project): VirtualFile? {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        var manifestFile: VirtualFile? = null

        val excluded = setOf("tests", "venv", ".idea", ".git", "__pycache__", "build", "dist", ".blender_stubs")
        fileIndex.iterateContent { file: VirtualFile ->
            if (!file.isDirectory) {
                if (file.name == "blender_manifest.toml") {
                    if (file.parent?.name !in excluded) {
                        manifestFile = file
                        return@iterateContent false
                    }
                }
            }
            true
        }
        return manifestFile
    }

    fun normalizeModuleName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(" ", "_")
            .replace("-", "_")
    }
}