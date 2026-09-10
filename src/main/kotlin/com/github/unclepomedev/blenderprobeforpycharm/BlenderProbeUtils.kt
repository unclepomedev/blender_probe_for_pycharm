package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.openapi.vfs.VirtualFile
import java.util.*

/**
 * Utility functions for Blender Addon development. Provides pure helper methods for module name
 * normalization, directory exclusion checks, and candidate sorting.
 */
object BlenderProbeUtils {

    private val EXCLUDED_DIR_NAMES =
        setOf(
            "tests",
            "venv",
            ".idea",
            ".git",
            "__pycache__",
            "build",
            "dist",
            ".blender_stubs",
        )

    /**
     * Sorts candidate manifest files prioritizing:
     * 1. Shallowest path (fewer path segments / path depth)
     * 2. Lexicographical order of path (for deterministic ordering)
     */
    fun sortCandidates(candidates: List<VirtualFile>): List<VirtualFile> {
        return candidates.sortedWith(
            compareBy<VirtualFile> { candidate ->
                    candidate.path.count { it == '/' || it == '\\' }
                }
                .thenBy { it.path }
        )
    }

    /**
     * Checks if a file has any ancestor directory in [EXCLUDED_DIR_NAMES] up to [contentRoot].
     * Traversal stops when reaching [contentRoot] or when an excluded directory is found.
     */
    fun isUnderExcludedDirectory(
        file: VirtualFile,
        contentRoot: VirtualFile? = null,
    ): Boolean {
        var parent: VirtualFile? = file.parent
        while (parent != null) {
            if (parent.name in EXCLUDED_DIR_NAMES) {
                return true
            }
            if (contentRoot != null && parent == contentRoot) {
                break
            }
            parent = parent.parent
        }
        return false
    }

    /**
     * Normalizes a string to be a valid Python module name. Converts to lowercase and replaces
     * spaces and hyphens with underscores.
     *
     * @param name The original name.
     * @return The normalized module name.
     */
    fun normalizeModuleName(name: String): String {
        return name.lowercase(Locale.ROOT).replace(" ", "_").replace("-", "_")
    }
}
