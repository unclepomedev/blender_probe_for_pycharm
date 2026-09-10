package com.github.unclepomedev.blenderprobeforpycharm.services

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File

/** Responsible for finding and detecting Blender add-on manifests within a project. */
internal class BlenderAddonDetector(private val project: Project) {

    /**
     * Scans the project content for `blender_manifest.toml` files and detects the add-on structure.
     * Honours manifest file override setting when configured.
     */
    fun detect(): AddonDetectionResult {
        val overridePath = BlenderSettings.getInstance(project).state.manifestPath.trim()
        if (overridePath.isNotEmpty()) {
            return detectOverride(overridePath)
        }

        val candidates = findCandidateManifestFiles()
        val sortedCandidates = BlenderProbeUtils.sortCandidates(candidates)

        val chosen = sortedCandidates.firstOrNull()
        return if (chosen != null) {
            val rejected = sortedCandidates.drop(1)
            createResultFromManifest(chosen, rejected)
        } else {
            createFallbackResult()
        }
    }

    /** Finds the primary manifest entry file for the addon, or null if none found. */
    fun findPrimaryManifest(): VirtualFile? {
        val overridePath = BlenderSettings.getInstance(project).state.manifestPath.trim()
        if (overridePath.isNotEmpty()) {
            val virtualFile = findVirtualFileByPath(overridePath)
            if (
                virtualFile == null ||
                    !virtualFile.isValid ||
                    virtualFile.isDirectory ||
                    virtualFile.name != MANIFEST_FILE_NAME
            ) {
                return null
            }
            return virtualFile
        }

        val candidates = findCandidateManifestFiles()
        return BlenderProbeUtils.sortCandidates(candidates).firstOrNull()
    }

    private fun detectOverride(overridePath: String): AddonDetectionResult {
        val virtualFile = findVirtualFileByPath(overridePath)
        if (virtualFile == null || !virtualFile.isValid || !virtualFile.exists()) {
            return createInvalidOverrideResult(overridePath)
        }
        if (virtualFile.isDirectory) {
            return createInvalidOverrideResult(overridePath)
        }
        if (virtualFile.name != MANIFEST_FILE_NAME) {
            return createInvalidOverrideResult(overridePath)
        }

        return createResultFromManifest(virtualFile, emptyList())
    }

    private fun findVirtualFileByPath(path: String): VirtualFile? {
        val vfm = VirtualFileManager.getInstance()
        val direct = vfm.findFileByUrl(path)
        if (direct != null) return direct

        val local = LocalFileSystem.getInstance().findFileByPath(path)
        if (local != null) return local

        val ioFile = File(path)
        val fromIo = LocalFileSystem.getInstance().findFileByIoFile(ioFile)
        if (fromIo != null) return fromIo

        val withFileUrl = vfm.findFileByUrl("file://$path")
        if (withFileUrl != null) return withFileUrl

        // In test fixtures, temp:/// URL protocol is used, where path starts with "/src/..."
        return vfm.findFileByUrl("temp://$path") ?: vfm.findFileByUrl("temp:///$path")
    }

    private fun createInvalidOverrideResult(overridePath: String): AddonDetectionResult {
        return AddonDetectionResult(
            manifestPath = null,
            moduleName = BlenderProbeUtils.normalizeModuleName(project.name),
            sourceRoot = null,
            rejectedCandidates = emptyList(),
            invalidOverridePath = overridePath,
        )
    }

    private fun createResultFromManifest(
        manifestFile: VirtualFile,
        rejectedCandidates: List<VirtualFile>,
    ): AddonDetectionResult {
        val addonDir = manifestFile.parent
        val moduleName = addonDir?.name ?: BlenderProbeUtils.normalizeModuleName(project.name)
        val sourceRoot = addonDir?.parent?.path
        return AddonDetectionResult(
            manifestPath = manifestFile.path,
            moduleName = moduleName,
            sourceRoot = sourceRoot,
            rejectedCandidates = rejectedCandidates,
        )
    }

    private fun createFallbackResult(): AddonDetectionResult {
        return AddonDetectionResult(
            manifestPath = null,
            moduleName = BlenderProbeUtils.normalizeModuleName(project.name),
            sourceRoot = null,
            rejectedCandidates = emptyList(),
        )
    }

    private fun findCandidateManifestFiles(): List<VirtualFile> {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val candidates = mutableListOf<VirtualFile>()

        fileIndex.iterateContent { file: VirtualFile ->
            if (isCandidateManifest(file, fileIndex)) {
                candidates.add(file)
            }
            true
        }
        return candidates
    }

    private fun isCandidateManifest(
        file: VirtualFile,
        fileIndex: com.intellij.openapi.roots.ProjectFileIndex,
    ): Boolean {
        if (file.isDirectory || file.name != MANIFEST_FILE_NAME) {
            return false
        }
        val contentRoot = fileIndex.getContentRootForFile(file)
        return !BlenderProbeUtils.isUnderExcludedDirectory(file, contentRoot)
    }

    companion object {
        const val MANIFEST_FILE_NAME = "blender_manifest.toml"
    }
}
