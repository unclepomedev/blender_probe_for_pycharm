package com.github.unclepomedev.blenderprobeforpycharm.run.app

import com.intellij.openapi.options.SettingsEditor
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class BlenderRunConfigurationEditor : SettingsEditor<BlenderRunConfiguration>() {

    private val myPanel: JPanel = FormBuilder.createFormBuilder()
        .addComponent(JLabel("Blender path is configured in Settings > Tools > Blender Probe."))
        .addComponent(JLabel("No additional run configuration settings required."))
        .panel

    override fun createEditor(): JComponent {
        return myPanel
    }

    override fun resetEditorFrom(s: BlenderRunConfiguration) {
    }

    override fun applyEditorTo(s: BlenderRunConfiguration) {
    }
}