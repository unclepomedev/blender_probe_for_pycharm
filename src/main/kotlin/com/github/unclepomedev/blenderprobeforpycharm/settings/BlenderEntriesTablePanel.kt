package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * UI component panel for managing the list of Blender executable entries. Provides a table with
 * add, remove, and edit actions.
 */
class BlenderEntriesTablePanel(
    private val project: Project,
    private val onEntriesChanged: () -> Unit,
) {

    private val tableModel =
        object : DefaultTableModel(arrayOf("Name", "Executable Path"), 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
    val entriesTable: JBTable = JBTable(tableModel)

    val panel: JComponent = createTableComponent()

    private fun createTableComponent(): JComponent {
        val decorator =
            ToolbarDecorator.createDecorator(entriesTable)
                .setAddAction { handleAddAction() }
                .setRemoveAction { handleRemoveAction() }
                .setEditAction { handleEditAction() }
                .disableUpDownActions()

        val tablePanel = JPanel(BorderLayout())
        tablePanel.border = BorderFactory.createTitledBorder("Configured Blender Executables")
        tablePanel.add(decorator.createPanel(), BorderLayout.CENTER)
        return tablePanel
    }

    private fun handleAddAction() {
        val dialog = BlenderEntryDialog(project, "Add Blender Executable")
        if (dialog.showAndGet()) {
            val name = dialog.getEntryName()
            val path = dialog.getEntryPath()
            tableModel.addRow(arrayOf(name, path))
            onEntriesChanged()
        }
    }

    private fun handleRemoveAction() {
        val selectedRow = entriesTable.selectedRow
        if (selectedRow != -1) {
            tableModel.removeRow(selectedRow)
            onEntriesChanged()
        }
    }

    private fun handleEditAction() {
        val selectedRow = entriesTable.selectedRow
        if (selectedRow != -1) {
            val currentName = tableModel.getValueAt(selectedRow, 0) as String
            val currentPath = tableModel.getValueAt(selectedRow, 1) as String
            val dialog =
                BlenderEntryDialog(
                    project,
                    "Edit Blender Executable",
                    currentName,
                    currentPath,
                )
            if (dialog.showAndGet()) {
                val newName = dialog.getEntryName()
                val newPath = dialog.getEntryPath()
                tableModel.setValueAt(newName, selectedRow, 0)
                tableModel.setValueAt(newPath, selectedRow, 1)
                onEntriesChanged()
            }
        }
    }

    fun getEntries(): List<BlenderEntry> {
        val list = mutableListOf<BlenderEntry>()
        for (i in 0 until tableModel.rowCount) {
            val name = tableModel.getValueAt(i, 0) as String
            val path = tableModel.getValueAt(i, 1) as String
            list.add(BlenderEntry(name, path))
        }
        return list
    }

    fun setEntries(entries: List<BlenderEntry>) {
        tableModel.rowCount = 0
        for (entry in entries) {
            tableModel.addRow(arrayOf(entry.name, entry.path))
        }
    }

    fun findEntryByName(name: String?): BlenderEntry? {
        if (name.isNullOrBlank()) return null
        for (i in 0 until tableModel.rowCount) {
            val n = tableModel.getValueAt(i, 0) as String
            val p = tableModel.getValueAt(i, 1) as String
            if (n == name) {
                return BlenderEntry(n, p)
            }
        }
        return null
    }

    fun containsName(name: String): Boolean {
        for (i in 0 until tableModel.rowCount) {
            if (tableModel.getValueAt(i, 0) == name) {
                return true
            }
        }
        return false
    }
}
