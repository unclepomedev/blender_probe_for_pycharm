package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Represents a configured Blender executable entry.
 *
 * @property name A display name for the Blender binary (e.g. "Blender 5.2", "Daily Build").
 * @property path The absolute path to the Blender executable.
 */
data class BlenderEntry(
    var name: String = "",
    var path: String = "",
)

/**
 * Manages project-level settings for Blender Probe. Stores configuration such as the path to the
 * Blender executable.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "BlenderProbeSettings",
    storages = [Storage("blender_probe.xml")],
)
class BlenderSettings(private val project: Project) :
    PersistentStateComponent<BlenderSettings.State> {

    /**
     * Data class to hold the state of the settings.
     *
     * @property blenderPath Legacy path to the Blender executable. Kept for backwards
     *   compatibility.
     * @property entries List of configured Blender executables.
     * @property currentEntryName Name of the currently selected Blender executable from [entries].
     * @property useFactoryStartup Whether to launch Blender with the `--factory-startup` flag.
     *   Defaults to true to mirror the standard, supported behavior, skipping the user's
     *   `startup.blend` file across application runs, test runs, and stub generation.
     */
    data class State(
        var blenderPath: String = "",
        var entries: MutableList<BlenderEntry> = mutableListOf(),
        var currentEntryName: String = "",
        var useFactoryStartup: Boolean = true,
    )

    private var myState = State()

    companion object {
        private val LOG = Logger.getInstance(BlenderSettings::class.java)
        private const val BLUP_TIMEOUT_MS = 2000

        /**
         * Retrieves the instance of BlenderSettings for the given project.
         *
         * @param project The project to get settings for.
         * @return The BlenderSettings instance.
         */
        fun getInstance(project: Project): BlenderSettings = project.service()
    }

    /**
     * Returns the current state of the settings.
     *
     * @return The current state.
     */
    override fun getState(): State = myState

    /**
     * Loads the state of the settings from the provided state object.
     *
     * @param state The state to load.
     */
    override fun loadState(state: State) {
        myState = state
        migrateIfNeeded()
    }

    /** Migrates legacy single blenderPath to entries list if entries is empty. */
    private fun migrateIfNeeded() {
        if (myState.entries.isEmpty() && myState.blenderPath.isNotBlank()) {
            val entryName = File(myState.blenderPath).name.ifBlank { "Blender" }
            myState.entries.add(BlenderEntry(name = entryName, path = myState.blenderPath))
            myState.currentEntryName = entryName
        }
    }

    /** Returns the currently selected [BlenderEntry], or null if none is selected. */
    fun getSelectedEntry(): BlenderEntry? {
        if (myState.currentEntryName.isNotBlank()) {
            return myState.entries.find { it.name == myState.currentEntryName }
        }
        return null
    }

    /**
     * Resolves the path to the Blender executable. If a configured entry is selected, its path is
     * returned. Otherwise, falls back to legacy blenderPath or attempts to detect the path using
     * the 'blup' tool.
     *
     * @return The resolved Blender path, or null if not found.
     */
    fun resolveBlenderPath(): String? {
        val selected = getSelectedEntry()
        if (selected != null && selected.path.isNotBlank()) {
            return selected.path
        }

        if (myState.blenderPath.isNotBlank()) {
            return myState.blenderPath
        }

        return detectPathViaBlup()
    }

    /**
     * Sets the active Blender entry by name. Updates both [State.currentEntryName] and
     * [State.blenderPath]. If [name] does not match any entry, [State.blenderPath] is cleared.
     */
    fun setActiveEntry(name: String) {
        myState.currentEntryName = name
        val entry = myState.entries.find { it.name == name }
        if (entry != null) {
            myState.blenderPath = entry.path
        } else {
            myState.blenderPath = ""
        }
    }

    private fun detectPathViaBlup(): String? {
        val basePath = project.basePath

        try {
            val cmd =
                GeneralCommandLine("blup", "which")
                    .apply {
                        if (basePath != null && File(basePath).isDirectory) {
                            withWorkDirectory(basePath)
                        }
                    }
                    .withCharset(StandardCharsets.UTF_8)
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

            val handler = CapturingProcessHandler(cmd)
            val output = handler.runProcess(BLUP_TIMEOUT_MS)

            if (output.exitCode == 0) {
                val path = output.stdout.trim()
                if (path.isNotBlank()) {
                    LOG.info("Blender path resolved via blup: $path")
                    return path
                }
            } else {
                LOG.debug("blup which returned non-zero exit code: ${output.exitCode}")
            }
        } catch (e: ExecutionException) {
            LOG.debug("Blup executable not found or failed to start: ${e.message}")
        } catch (e: Exception) {
            LOG.warn("Unexpected error while detecting blender via blup: ${e.message}")
        }
        return null
    }
}
