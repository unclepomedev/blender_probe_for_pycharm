package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.openapi.vfs.VirtualFile

/** Specific reason why a configured manifest path override is invalid. */
enum class ManifestOverrideFailure {
    NOT_FOUND,
    IS_DIRECTORY,
    NOT_MANIFEST_NAME,
}

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
 * @property invalidOverridePath The path configured by the user that failed validation, if any.
 * @property invalidOverrideReason The specific reason the override failed validation, if any.
 */
data class AddonDetectionResult(
    val manifestPath: String?,
    val moduleName: String,
    val sourceRoot: String?,
    val rejectedCandidates: List<VirtualFile> = emptyList(),
    val invalidOverridePath: String? = null,
    val invalidOverrideReason: ManifestOverrideFailure? = null,
) {
    val isAmbiguous: Boolean
        get() = rejectedCandidates.isNotEmpty()
}
