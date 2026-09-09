package com.github.unclepomedev.blenderprobeforpycharm

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

/**
 * Utility functions for Blender Addon development. Provides helper methods to detect addon
 * information from the project structure.
 */
object BlenderProbeUtils {
    /**
     * Represents the result of detecting a Blender addon in a project.
     *
     * @property manifestPath The absolute path to the chosen `blender_manifest.toml`, or null if
     *   none found.
     * @property moduleName The detected Python module name (from manifest directory, or fallback
     *   normalized project name).
     * @property sourceRoot The absolute path to the source root directory, or null if none found.
     * @property rejectedCandidates Other candidate `blender_manifest.toml` files found in the
     *   project but not chosen.
     */
    data class AddonDetectionResult(
        val manifestPath: String?,
        val moduleName: String,
        val sourceRoot: String?,
        val rejectedCandidates: List<VirtualFile> = emptyList(),
    ) {
        val isAmbiguous: Boolean
            get() = rejectedCandidates.isNotEmpty()
    }

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

    fun findAddonEntryFile(project: Project): VirtualFile? {
        val candidates = findCandidateManifestFiles(project)
        return sortCandidates(candidates).firstOrNull()
    }

    private fun sortCandidates(candidates: List<VirtualFile>): List<VirtualFile> {
        return candidates.sortedWith(
            compareBy<VirtualFile> { candidate ->
                    candidate.path.count { it == '/' || it == '\\' }
                }
                .thenBy { it.path }
        )
    }

    /**
     * Detects Blender addon information from the project structure.
     *
     * Collects all candidate `blender_manifest.toml` files that are not within excluded directories
     * (checking all ancestors up to the project root/content root).
     *
     * Candidates are prioritized by:
     * 1. Shallowest path (fewer path segments / path depth)
     * 2. Lexicographical order of path (for deterministic ordering)
     *
     * @param project The current project.
     * @return [AddonDetectionResult] containing the resolved manifest, module name, source root,
     *   and rejected candidates.
     */
    fun detectAddon(project: Project): AddonDetectionResult {
        val candidates = findCandidateManifestFiles(project)
        val sortedCandidates = sortCandidates(candidates)

        val chosen = sortedCandidates.firstOrNull()
        val rejected = if (sortedCandidates.isNotEmpty()) sortedCandidates.drop(1) else emptyList()

        if (chosen != null) {
            val addonDir = chosen.parent
            val moduleName = addonDir?.name ?: normalizeModuleName(project.name)
            val sourceRoot = addonDir?.parent?.path
            return AddonDetectionResult(
                manifestPath = chosen.path,
                moduleName = moduleName,
                sourceRoot = sourceRoot,
                rejectedCandidates = rejected,
            )
        }

        return AddonDetectionResult(
            manifestPath = null,
            moduleName = normalizeModuleName(project.name),
            sourceRoot = null,
            rejectedCandidates = emptyList(),
        )
    }

    /**
     * Detects the Python module name for the Blender addon. Attempts to find the module name from
     * the `blender_manifest.toml` location, or falls back to a normalized version of the project
     * name.
     *
     * @param project The current project.
     * @return The detected addon module name.
     */
    fun detectAddonModuleName(project: Project): String {
        return detectAddon(project).moduleName
    }

    /**
     * Locates the source root directory of the Blender addon. This is determined based on the
     * location of the `blender_manifest.toml` file.
     *
     * @param project The current project.
     * @return The absolute path to the source root, or null if not found.
     */
    fun getAddonSourceRoot(project: Project): String? {
        return detectAddon(project).sourceRoot
    }

    private fun findCandidateManifestFiles(project: Project): List<VirtualFile> {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val candidates = mutableListOf<VirtualFile>()

        fileIndex.iterateContent { file: VirtualFile ->
            if (!file.isDirectory && file.name == "blender_manifest.toml") {
                val contentRoot = fileIndex.getContentRootForFile(file)
                if (!isUnderExcludedDirectory(file, contentRoot)) {
                    candidates.add(file)
                }
            }
            true
        }
        return candidates
    }

    /**
     * Checks if a file has any ancestor directory in [EXCLUDED_DIR_NAMES] up to [contentRoot].
     * Traversal stops when reaching [contentRoot] or when an excluded directory is found.
     */
    internal fun isUnderExcludedDirectory(
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
