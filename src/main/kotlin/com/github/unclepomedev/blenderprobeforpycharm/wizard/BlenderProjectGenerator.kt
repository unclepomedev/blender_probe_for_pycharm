package com.github.unclepomedev.blenderprobeforpycharm.wizard

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.github.unclepomedev.blenderprobeforpycharm.icons.BlenderProbeIcons
import com.github.unclepomedev.blenderprobeforpycharm.run.BlenderTestConfigurationType
import com.github.unclepomedev.blenderprobeforpycharm.run.BlenderTestRunConfiguration
import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderStubService
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.ProjectGeneratorPeer
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class BlenderProjectGenerator : DirectoryProjectGenerator<Any> {

    override fun getName(): String = "Blender addon"
    override fun getLogo(): Icon = BlenderProbeIcons.Logo16

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: Any,
        module: Module
    ) {
        val rootIoFile = VfsUtil.virtualToIoFile(baseDir)

        val slug = BlenderProbeUtils.normalizeModuleName(project.name)
        val srcDir = File(rootIoFile, slug).apply { mkdirs() }
        val testsDir = File(rootIoFile, "tests").apply { mkdirs() }
        val props = mapOf(
            "ADDON_NAME" to project.name,
            "ADDON_NAME_SLUG" to slug,
            "AUTHOR" to (System.getProperty("user.name") ?: "Developer")
        )

        createFileFromTemplate("BlenderAddon_Manifest.toml", srcDir, "blender_manifest.toml", props)
        createFileFromTemplate("BlenderAddon_Init.py", srcDir, "__init__.py", props)
        createFileFromTemplate("BlenderAddon_Ops.py", srcDir, "operators.py", props)
        createFileFromTemplate("BlenderAddon_Panel.py", srcDir, "panel.py", props)
        createFileFromTemplate("BlenderAddon_RunTests.py", testsDir, "run_tests.py", props)
        createFileFromTemplate("BlenderAddon_Test.py", testsDir, "test_sample.py", props)
        createFileFromTemplate("BlenderAddon_License.txt", rootIoFile, "LICENSE", props)
        createFileFromTemplate("BlenderAddon_Pyproject.toml", rootIoFile, "pyproject.toml", props)
        createFileFromTemplate("BlenderAddon_Gitignore.gitignore", rootIoFile, ".gitignore", props)

        val githubDir = File(rootIoFile, ".github").apply { mkdirs() }
        val workflowsDir = File(githubDir, "workflows").apply { mkdirs() }

        createFileFromTemplate("BlenderAddon_Ci.yml", workflowsDir, "ci.yml", props)
        createFileFromTemplate("BlenderAddon_Dependabot.yml", githubDir, "dependabot.yml", props)

        VfsUtil.markDirtyAndRefresh(true, true, true, baseDir)
        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "Configuring Blender environment", false) {
                override fun run(indicator: ProgressIndicator) {
                    DumbService.getInstance(project).waitForSmartMode()
                    ApplicationManager.getApplication().invokeLater {
                        createDefaultRunConfiguration(project)
                    }

                    indicator.text = "Detecting Blender executable..."
                    val blenderPath = BlenderSettings.getInstance(project).resolveBlenderPath()

                    if (blenderPath != null) {
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                BlenderStubService.getInstance(project).generateStubs(blenderPath)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            })
    }

    private fun createDefaultRunConfiguration(project: Project) {
        val runManager = RunManager.getInstance(project)
        val type = ConfigurationTypeUtil.findConfigurationType(BlenderTestConfigurationType::class.java)
        val factory = type.configurationFactories.firstOrNull() ?: return

        if (runManager.findConfigurationByTypeAndName(type.id, "All Tests") != null) return

        val settings = runManager.createConfiguration("All Tests", factory)
        val config = settings.configuration as? BlenderTestRunConfiguration

        config?.let {
            it.testDir = "${project.basePath}/tests"
        }

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
    }

    override fun validate(baseDirPath: String): ValidationResult = ValidationResult.OK

    override fun createPeer(): ProjectGeneratorPeer<Any> {
        return object : ProjectGeneratorPeer<Any> {
            override fun getSettings(): Any = Any()

            override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
                return JPanel()
            }

            override fun buildUI(settingsStep: SettingsStep) {
            }

            override fun validate(): ValidationInfo? = null

            override fun isBackgroundJobRunning(): Boolean = false
        }
    }

    private fun createFileFromTemplate(templateName: String, dir: File, fileName: String, props: Map<String, Any>) {
        val manager = FileTemplateManager.getDefaultInstance()
        val template = manager.getInternalTemplate(templateName)
        val content = template.getText(props)
        File(dir, fileName).writeText(content)
    }
}