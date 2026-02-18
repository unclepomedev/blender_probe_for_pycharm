package com.github.unclepomedev.blenderprobeforpycharm.debugger

import com.github.unclepomedev.blenderprobeforpycharm.icons.BlenderProbeIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue

class ForceBlenderRedrawAction : AnAction() {

    init {
        templatePresentation.text = "Force Blender Redraw"
        templatePresentation.description = "Updates Blender viewport manually"
        templatePresentation.icon = BlenderProbeIcons.Logo16
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val session = XDebuggerManager.getInstance(project).currentSession ?: return
        val evaluator = session.debugProcess.evaluator ?: return

        val scriptContent = try {
            this.javaClass.classLoader.getResource("python/redraw.py")?.readText()
        } catch (_: Exception) {
            null
        } ?: return

        evaluator.evaluate(scriptContent, object : XDebuggerEvaluator.XEvaluationCallback {
            override fun evaluated(result: XValue) {}
            override fun errorOccurred(errorMessage: String) {
                println("Blender Probe Redraw Error: $errorMessage")
            }
        }, null)
    }
}