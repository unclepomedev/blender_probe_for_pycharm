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
                            "Enabled by default. Disable to let Blender load modules installed in your user " +
                                "environment &mdash; useful for legacy add-ons whose dependencies are installed there."
                        )
                }
            }
            group("Legacy Add-on") {
                row("Add-on name (fallback):") {
                    textField()
                        .bindText(settings.state::fallbackAddonName)
                        .comment(
                            "Used only when no <code>blender_manifest.toml</code> is found.<br>" +
                                "Set the add-on's Python module name to activate. Leave empty to use the project name."
                        )
                        .align(AlignX.FILL)
                }
            }
        }
    }
}