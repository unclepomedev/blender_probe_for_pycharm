package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Provides configuration UI for Blender Probe settings. Allows managing a list of Blender
 * executables like Python interpreters in PyCharm, and easily switching the active binary.
 */
class BlenderSettingsConfigurable(project: Project) : Configurable {

    companion object {
        const val AUTO_DETECT_OPTION = "<Auto-detect via blup>"
    }

    private val settings = BlenderSettings.getInstance(project)

    private val currentExecutableComboBox = ComboBox<String>()
    private val useFactoryStartupCheckBox =
        JBCheckBox(
            "Launch Blender with --factory-startup",
            true,
        )

    private val entriesTablePanel =
        BlenderEntriesTablePanel(project) {
            handleEntriesChanged()
        }

    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Blender Probe"

    override fun createComponent(): JComponent {
        setupComboBoxRenderer()

        val topForm = createTopFormPanel()
        val rootPanel =
            JPanel(BorderLayout(0, JBUI.scale(10))).apply {
                add(topForm, BorderLayout.NORTH)
                add(entriesTablePanel.panel, BorderLayout.CENTER)
            }

        mainPanel = rootPanel
        reset()
        return rootPanel
    }

    private fun setupComboBoxRenderer() {
        currentExecutableComboBox.renderer =
            SimpleListCellRenderer.create("") { value ->
                if (value == AUTO_DETECT_OPTION) {
                    "$AUTO_DETECT_OPTION (Recommended if blup is configured)"
                } else {
                    val matchingEntry = entriesTablePanel.findEntryByName(value)
                    if (matchingEntry != null) {
                        "${matchingEntry.name} (${matchingEntry.path})"
                    } else {
                        value ?: ""
                    }
                }
            }
    }

    private fun createTopFormPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Active Blender executable:", currentExecutableComboBox)
            .addComponent(useFactoryStartupCheckBox)
            .panel
    }

    private fun handleEntriesChanged() {
        val currentSelection = currentExecutableComboBox.selectedItem as? String
        val newSelection =
            if (currentSelection != null && entriesTablePanel.containsName(currentSelection)) {
                currentSelection
            } else {
                AUTO_DETECT_OPTION
            }
        updateComboBox(selectName = newSelection)
    }

    private fun updateComboBox(selectName: String? = null) {
        val model = CollectionComboBoxModel<String>()
        model.add(AUTO_DETECT_OPTION)
        for ((name) in entriesTablePanel.getEntries()) {
            model.add(name)
        }
        currentExecutableComboBox.model = model

        if (
            selectName != null &&
                (selectName == AUTO_DETECT_OPTION || entriesTablePanel.containsName(selectName))
        ) {
            currentExecutableComboBox.selectedItem = selectName
        } else {
            currentExecutableComboBox.selectedItem = AUTO_DETECT_OPTION
        }
    }

    override fun isModified(): Boolean {
        val currentEntries = entriesTablePanel.getEntries()
        if (currentEntries != settings.state.entries) return true

        val selectedItem = currentExecutableComboBox.selectedItem as? String
        val activeName =
            if (selectedItem == AUTO_DETECT_OPTION || selectedItem == null) "" else selectedItem
        if (activeName != settings.state.currentEntryName) return true

        if (useFactoryStartupCheckBox.isSelected != settings.state.useFactoryStartup) return true

        return false
    }

    override fun apply() {
        settings.state.entries = entriesTablePanel.getEntries().toMutableList()

        val selectedItem = currentExecutableComboBox.selectedItem as? String
        if (selectedItem == null || selectedItem == AUTO_DETECT_OPTION) {
            settings.state.currentEntryName = ""
            settings.state.blenderPath = ""
        } else {
            settings.setActiveEntry(selectedItem)
        }

        settings.state.useFactoryStartup = useFactoryStartupCheckBox.isSelected
    }

    override fun reset() {
        entriesTablePanel.setEntries(settings.state.entries)
        useFactoryStartupCheckBox.isSelected = settings.state.useFactoryStartup

        val targetSelection = resolveTargetSelection()
        updateComboBox(selectName = targetSelection)
    }

    private fun resolveTargetSelection(): String {
        val state = settings.state
        return when {
            state.currentEntryName.isNotBlank() &&
                state.entries.any { it.name == state.currentEntryName } -> state.currentEntryName

            state.blenderPath.isNotBlank() && state.entries.any { it.path == state.blenderPath } ->
                state.entries.first { it.path == state.blenderPath }.name

            else -> AUTO_DETECT_OPTION
        }
    }
}
