package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/**
 * Settings editor for the Blender Test run configuration.
 * Provides a UI for selecting the test directory.
 */
class BlenderTestSettingsEditor : SettingsEditor<BlenderTestRunConfiguration>() {

    private val testDirField = TextFieldWithBrowseButton()

    /**
     * Creates the editor component.
     *
     * @return The editor component.
     */
    override fun createEditor(): JComponent {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Test Directory")
            .withDescription("Select the directory containing your Blender Python tests")

        testDirField.addBrowseFolderListener(
            null,
            descriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Test directory:", testDirField)
            .panel
    }

    override fun resetEditorFrom(s: BlenderTestRunConfiguration) {
        testDirField.text = s.testDir
    }

    override fun applyEditorTo(s: BlenderTestRunConfiguration) {
        s.testDir = testDirField.text
    }
}
