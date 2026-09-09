package com.github.unclepomedev.blenderprobeforpycharm.manifest

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

/** Details about a candidate `blender_manifest.toml` that was rejected due to exclusion rules. */
data class ExcludedCandidate(
    val path: String,
    val reason: String,
)

/**
 * Represents the result of detecting a Blender addon in a project.
 *
 * @property manifestPath The absolute path to the chosen `blender_manifest.toml`, or null if none
 *   found.
 * @property moduleName The detected Python module name (from manifest directory, or fallback
 *   normalized project name).
 * @property sourceRoot The absolute path to the source root directory, or null if none found.
 * @property rejectedCandidates Other candidate `blender_manifest.toml` files found in the project
 *   but not chosen.
 * @property excludedCandidates Candidate `blender_manifest.toml` files rejected because of excluded
 *   directories.
 * @property searchedRoots List of root directories searched during detection.
 */
data class AddonDetectionResult(
    val manifestPath: String?,
    val moduleName: String,
    val sourceRoot: String?,
    val rejectedCandidates: List<VirtualFile> = emptyList(),
    val excludedCandidates: List<ExcludedCandidate> = emptyList(),
    val searchedRoots: List<String> = emptyList(),
) {
    val isAmbiguous: Boolean
        get() = rejectedCandidates.isNotEmpty()

    val isResolved: Boolean
        get() = manifestPath != null

    val totalCandidatesCount: Int
        get() = (if (manifestPath != null) 1 else 0) + rejectedCandidates.size

    /**
     * Formats a short, self-explanatory message summarizing the detection outcome. Keeps messages
     * to 1-2 lines plus paths.
     */
    fun formatMessage(): String {
        return if (isResolved) {
            buildString {
                append("Selected manifest: ").append(manifestPath)
                append("\nDerived source root: ").append(sourceRoot ?: "Unknown")
                if (totalCandidatesCount > 1) {
                    append("\nFound ")
                        .append(totalCandidatesCount)
                        .append(" candidate manifests in project.")
                }
            }
        } else {
            buildString {
                append("No valid blender_manifest.toml found.")
                if (searchedRoots.isNotEmpty()) {
                    append("\nSearched in: ").append(searchedRoots.joinToString(", "))
                }
                if (excludedCandidates.isNotEmpty()) {
                    val details =
                        excludedCandidates.joinToString("; ") { "${it.path} (${it.reason})" }
                    append("\nRejected candidates: ").append(details)
                }
            }
        }
    }
}

/**
 * Service/helper object responsible for detecting Blender add-on manifests and project structure.
 */
object BlenderManifestDetector {
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
        val scan = scanManifestFiles(project)
        return sortCandidates(scan.candidates).firstOrNull()
    }

    internal fun sortCandidates(candidates: List<VirtualFile>): List<VirtualFile> {
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
        val scan = scanManifestFiles(project)
        val sortedCandidates = sortCandidates(scan.candidates)

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
                excludedCandidates = scan.excludedCandidates,
                searchedRoots = scan.searchedRoots,
            )
        }

        return AddonDetectionResult(
            manifestPath = null,
            moduleName = normalizeModuleName(project.name),
            sourceRoot = null,
            rejectedCandidates = emptyList(),
            excludedCandidates = scan.excludedCandidates,
            searchedRoots = scan.searchedRoots,
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

    private data class ManifestScanResult(
        val candidates: List<VirtualFile>,
        val excludedCandidates: List<ExcludedCandidate>,
        val searchedRoots: List<String>,
    )

    private fun scanManifestFiles(project: Project): ManifestScanResult {
        val rootManager = ProjectRootManager.getInstance(project)
        val fileIndex = rootManager.fileIndex
        val candidates = mutableListOf<VirtualFile>()
        val excluded = mutableListOf<ExcludedCandidate>()

        val searchedRoots =
            rootManager.contentRoots
                .map { it.path }
                .ifEmpty {
                    listOfNotNull(project.basePath)
                }

        fileIndex.iterateContent { file: VirtualFile ->
            if (!file.isDirectory && file.name == "blender_manifest.toml") {
                val contentRoot = fileIndex.getContentRootForFile(file)
                val exclusionReason = getExclusionReason(file, contentRoot)
                if (exclusionReason == null) {
                    candidates.add(file)
                } else {
                    excluded.add(ExcludedCandidate(file.path, exclusionReason))
                }
            }
            true
        }

        return ManifestScanResult(
            candidates = candidates,
            excludedCandidates = excluded,
            searchedRoots = searchedRoots,
        )
    }

    /**
     * Checks if a file has any ancestor directory in [EXCLUDED_DIR_NAMES] up to [contentRoot].
     * Returns the exclusion reason (e.g. "inside excluded directory 'venv'"), or null if not
     * excluded.
     */
    internal fun getExclusionReason(
        file: VirtualFile,
        contentRoot: VirtualFile? = null,
    ): String? {
        var parent: VirtualFile? = file.parent
        while (parent != null) {
            if (contentRoot != null && parent == contentRoot) {
                break
            }
            if (parent.name in EXCLUDED_DIR_NAMES) {
                return "inside excluded directory '${parent.name}'"
            }
            parent = parent.parent
        }
        return null
    }

    /**
     * Checks if a file has any ancestor directory in [EXCLUDED_DIR_NAMES] up to [contentRoot].
     * Traversal stops when reaching [contentRoot] or when an excluded directory is found.
     */
    internal fun isUnderExcludedDirectory(
        file: VirtualFile,
        contentRoot: VirtualFile? = null,
    ): Boolean {
        return getExclusionReason(file, contentRoot) != null
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
