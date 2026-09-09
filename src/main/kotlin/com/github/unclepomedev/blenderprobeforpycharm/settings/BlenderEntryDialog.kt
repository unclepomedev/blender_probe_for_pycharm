package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.io.File
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/** Dialog for adding or editing a Blender executable entry. */
class BlenderEntryDialog(
    project: Project,
    title: String,
    initialName: String = "",
    initialPath: String = "",
) : DialogWrapper(project) {

    private val nameField = JBTextField(initialName)
    private val pathField =
        TextFieldWithBrowseButton().apply {
            text = initialPath
            val descriptor =
                FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
                    .withTitle("Select Blender Executable")
            addBrowseFolderListener(project, descriptor)
            textField.document.addDocumentListener(
                object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) {
                        autoFillNameIfBlank()
                    }
                }
            )
        }

    init {
        this.title = title
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField)
            .addLabeledComponent("Executable path:", pathField)
            .panel
    }

    fun getEntryName(): String = nameField.text.trim()

    fun getEntryPath(): String = pathField.text.trim()

    override fun doOKAction() {
        if (getEntryName().isBlank()) {
            Messages.showErrorDialog(
                "Please enter a name for the Blender binary.",
                "Validation Error",
            )
            return
        }
        if (getEntryPath().isBlank()) {
            Messages.showErrorDialog(
                "Please specify the Blender executable path.",
                "Validation Error",
            )
            return
        }
        super.doOKAction()
    }

    private fun autoFillNameIfBlank() {
        if (nameField.text.isBlank()) {
            val file = File(pathField.text.trim())
            if (file.name.isNotBlank()) {
                nameField.text = file.nameWithoutExtension.ifBlank { file.name }
            }
        }
    }
}
