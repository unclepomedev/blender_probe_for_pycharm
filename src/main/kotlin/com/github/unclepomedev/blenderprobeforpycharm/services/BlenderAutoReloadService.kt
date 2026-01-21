package com.github.unclepomedev.blenderprobeforpycharm.services

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm

@Service(Service.Level.PROJECT)
class BlenderAutoReloadService(private val project: Project) : Disposable {

    @Suppress("UnstableApiUsage")
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val delayMillis = 500

    fun scheduleReload() {
        alarm.cancelAllRequests()
        alarm.addRequest({
            performReload()
        }, delayMillis)
    }

    private fun performReload() {
        if (BlenderProbeManager.activePort == null) return

        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction("com.github.unclepomedev.blenderprobeforpycharm.actions.ReloadAddonAction")

        if (action != null) {
            val frame = WindowManager.getInstance().getFrame(project)
            val component = frame?.contentPane

            actionManager.tryToExecute(action, null, component, "BlenderAutoReload", true)

            println("Auto-Reload triggered for ${project.name}")
        }
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }
}