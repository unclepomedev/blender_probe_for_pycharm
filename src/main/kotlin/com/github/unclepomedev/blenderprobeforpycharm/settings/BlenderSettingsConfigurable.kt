package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.AlignX

/**
 * Provides the configuration UI for Blender Probe settings.
 * Allows users to specify the path to the Blender executable.
 */
class BlenderSettingsConfigurable(private val project: Project) : BoundConfigurable("Blender Probe") {

    private val settings = BlenderSettings.getInstance(project)

    /**
     * Creates the settings panel.
     *
     * @return The created DialogPanel.
     */
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
                row {
                    checkBox("Launch Blender with <code>--factory-startup</code>")
                        .bindSelected(settings.state::useFactoryStartup)
                        .comment(
                            "Enabled by default (recommended). Disable only if your add-on relies on dependencies " +
                                "installed in your Blender user environment.<br>" +
                                "<b>Warning:</b> disabling it also loads third-party add-ons and may crash Blender " +
                                "on startup. Use at your own risk &mdash; this path is outside the supported scope."
                        )
                }
            }
        }
    }
}