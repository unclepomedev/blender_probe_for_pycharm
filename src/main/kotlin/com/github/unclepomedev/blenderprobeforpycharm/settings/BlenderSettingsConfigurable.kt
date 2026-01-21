package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.AlignX

class BlenderSettingsConfigurable(private val project: Project) : BoundConfigurable("Blender Probe") {

    private val settings = BlenderSettings.getInstance(project)

    override fun createPanel(): DialogPanel {
        return panel {
            group("General") {
                row("Blender executable:") {
                    val descriptor = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
                        .withTitle("Select Blender Executable")
                    textFieldWithBrowseButton(
                        project = project,
                        fileChooserDescriptor = descriptor
                    )
                        .bindText(settings.state::blenderPath)
                        .comment("Leave empty to auto-detect via <code>blup</code> (Recommended).<br>Or specify the absolute path to override.")
                        .align(AlignX.FILL)
                }
            }
        }
    }
}