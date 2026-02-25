package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

/**
 * Utility functions for Blender Addon development.
 * Provides helper methods to detect addon information from the project structure.
 */
object BlenderProbeUtils {
    /**
     * Detects the Python module name for the Blender addon.
     * Attempts to find the module name from the `blender_manifest.toml` location,
     * or falls back to a normalized version of the project name.
     *
     * @param project The current project.
     * @return The detected addon module name.
     */
    fun detectAddonModuleName(project: Project): String {
        val targetFile = findAddonEntryFile(project)

        if (targetFile != null) {
            return targetFile.parent.name
        }
        return normalizeModuleName(project.name)
    }

    /**
     * Locates the source root directory of the Blender addon.
     * This is determined based on the location of the `blender_manifest.toml` file.
     *
     * @param project The current project.
     * @return The absolute path to the source root, or null if not found.
     */
    fun getAddonSourceRoot(project: Project): String? {
        val targetFile = findAddonEntryFile(project) ?: return null
        val addonDir = targetFile.parent ?: return null
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

    /**
     * Normalizes a string to be a valid Python module name.
     * Converts to lowercase and replaces spaces and hyphens with underscores.
     *
     * @param name The original name.
     * @return The normalized module name.
     */
    fun normalizeModuleName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(" ", "_")
            .replace("-", "_")
    }
}