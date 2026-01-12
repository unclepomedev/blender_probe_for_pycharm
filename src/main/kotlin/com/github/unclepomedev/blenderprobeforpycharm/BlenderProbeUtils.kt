package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

object BlenderProbeUtils {
    fun findAddonPackageName(project: Project): String? {
        val basePath = project.basePath ?: return null
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null

        val excluded = setOf("tests", "venv", ".idea", ".git", "__pycache__", "build", "dist", ".blender_stubs")

        for (child in baseDir.children) {
            if (child.isDirectory && child.name !in excluded) {
                if (child.findChild("__init__.py") != null) {
                    return child.name
                }
            }
        }
        return null
    }
}