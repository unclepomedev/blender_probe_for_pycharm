package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.openapi.vfs.VirtualFile

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
 */
data class AddonDetectionResult(
    val manifestPath: String?,
    val moduleName: String,
    val sourceRoot: String?,
    val rejectedCandidates: List<VirtualFile> = emptyList(),
    val invalidOverridePath: String? = null,
) {
    val isAmbiguous: Boolean
        get() = rejectedCandidates.isNotEmpty()
}
