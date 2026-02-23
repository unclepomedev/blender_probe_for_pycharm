package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets

/**
 * Manages project-level settings for Blender Probe.
 * Stores configuration such as the path to the Blender executable.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "BlenderProbeSettings",
    storages = [Storage("blender_probe.xml")]
)
class BlenderSettings(private val project: Project) : PersistentStateComponent<BlenderSettings.State> {

    /**
     * Data class to hold the state of the settings.
     *
     * @property blenderPath The path to the Blender executable.
     */
    data class State(
        var blenderPath: String = ""
    )

    private var myState = State()

    companion object {
        private val LOG = Logger.getInstance(BlenderSettings::class.java)

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
    }

    /**
     * Resolves the path to the Blender executable.
     * If a path is configured in settings, it is returned.
     * Otherwise, it attempts to detect the path using the 'blup' tool.
     *
     * @return The resolved Blender path, or null if not found.
     */
    fun resolveBlenderPath(): String? {
        if (myState.blenderPath.isNotBlank()) {
            return myState.blenderPath
        }

        return detectPathViaBlup()
    }

    private fun detectPathViaBlup(): String? {
        val basePath = project.basePath ?: return null

        try {
            val cmd = GeneralCommandLine("blup", "which")
                .withWorkDirectory(basePath)
                .withCharset(StandardCharsets.UTF_8)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

            val handler = CapturingProcessHandler(cmd)
            val output = handler.runProcess(2000) // 2 sec timeout

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