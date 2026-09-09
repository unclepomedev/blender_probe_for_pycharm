package com.github.unclepomedev.blenderprobeforpycharm.services

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service that handles automatic reloading of the Blender add-on. Schedules a reload action when
 * changes are detected, with debouncing.
 */
@Service(Service.Level.PROJECT)
class BlenderAutoReloadService(
    private val project: Project,
    private val cs: CoroutineScope,
) {

    private var reloadJob: Job? = null
    private val delayMillis = 500L

    /**
     * Schedules a reload of the add-on. If a reload is already scheduled, it resets the timer
     * (debounce).
     */
    fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = cs.launch {
            delay(delayMillis.milliseconds)
            withContext(Dispatchers.EDT) {
                performReload()
            }
        }
    }

    private fun performReload() {
        if (BlenderProbeManager.activePort == null) return

        val actionManager = ActionManager.getInstance()
        val action =
            actionManager.getAction(
                "com.github.unclepomedev.blenderprobeforpycharm.actions.ReloadAddonAction"
            )

        if (action != null) {
            val frame = WindowManager.getInstance().getFrame(project)
            val component = frame?.contentPane

            actionManager.tryToExecute(action, null, component, "BlenderAutoReload", true)

            println("Auto-Reload triggered for ${project.name}")
        }
    }
}
