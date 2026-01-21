package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets

@Service(Service.Level.PROJECT)
@State(
    name = "BlenderProbeSettings",
    storages = [Storage("blender_probe.xml")]
)
class BlenderSettings(private val project: Project) : PersistentStateComponent<BlenderSettings.State> {

    data class State(
        var blenderPath: String = ""
    )

    private var myState = State()

    companion object {
        private val LOG = Logger.getInstance(BlenderSettings::class.java)
        fun getInstance(project: Project): BlenderSettings = project.service()
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

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