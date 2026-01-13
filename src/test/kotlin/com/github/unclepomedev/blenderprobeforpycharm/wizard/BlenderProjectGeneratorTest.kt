package com.github.unclepomedev.blenderprobeforpycharm.wizard

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeUtils
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

class BlenderProjectGeneratorTest : BaseBlenderTest() {

    fun testGenerateProjectStructure() {
        val generator = BlenderProjectGenerator()
        val settings = BlenderSettings.getInstance(project)

        val prevBlenderPath = settings.state.blenderPath

        try {
            settings.state.blenderPath = "/dummy/path/to/blender"

            val basePath = project.basePath ?: error("Project base path is null")
            val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
                ?: error("VirtualFile not found for $basePath")
            val module = myFixture.module

            generator.generateProject(project, baseDir, Any(), module)
            baseDir.refresh(false, true)

            assertNotNull("License should be created", baseDir.findChild("LICENSE"))

            val gitignore = baseDir.findChild(".gitignore")
            assertNotNull("GitIgnore should be created", gitignore)
            gitignore?.let {
                val content = VfsUtil.loadText(it)
                assertTrue("Should ignore .blender_stubs", content.contains(".blender_stubs/"))
                assertTrue("Should ignore .vscode", content.contains(".vscode/"))
                assertTrue("Should ignore .venv", content.contains(".venv/"))
            }

            val pyproject = baseDir.findChild("pyproject.toml")
            assertNotNull("pyproject.toml should be created", pyproject)
            pyproject?.let {
                val content = VfsUtil.loadText(it)
                val expectedSlug = BlenderProbeUtils.normalizeModuleName(project.name)
                assertTrue("Should contain project name", content.contains("name = \"$expectedSlug\""))
                assertTrue("Should contain pip dependency", content.contains("dependencies = [\"pip\"]"))
                assertTrue("Should use hatchling", content.contains("build-backend = \"hatchling.build\""))
            }

            val expectedSlug = BlenderProbeUtils.normalizeModuleName(project.name)
            val packageDir = baseDir.findChild(expectedSlug)

            assertNotNull("Package directory '$expectedSlug' should exist", packageDir)
            packageDir?.let { dir ->
                assertNotNull("__init__.py missing", dir.findChild("__init__.py"))
                assertNotNull("operators.py missing", dir.findChild("operators.py"))
                assertNotNull("panel.py missing", dir.findChild("panel.py"))

                val manifestFile = dir.findChild("blender_manifest.toml")
                assertNotNull("Manifest should be inside package dir", manifestFile)

                if (manifestFile != null) {
                    val manifestContent = VfsUtil.loadText(manifestFile)
                    assertFalse("Placeholder replaced", manifestContent.contains("\${ADDON_NAME}"))
                    assertTrue("Slug injected", manifestContent.contains("id = \"$expectedSlug\""))
                    assertTrue("GPL License should be specified", manifestContent.contains("SPDX:GPL-3.0-or-later"))
                }
            }

            val testsDir = baseDir.findChild("tests")
            assertNotNull("tests directory should exist", testsDir)
            testsDir?.let { dir ->
                assertNotNull("test_sample.py missing", dir.findChild("test_sample.py"))
                assertNotNull("run_tests.py missing (Required for CI)", dir.findChild("run_tests.py"))
            }

            val githubDir = baseDir.findChild(".github")
            assertNotNull(".github directory should exist", githubDir)

            val workflowsDir = githubDir?.findChild("workflows")
            assertNotNull("workflows directory should exist", workflowsDir)

            workflowsDir?.let { dir ->
                val ciFile = dir.findChild("ci.yml")
                assertNotNull("CI workflow file missing", ciFile)
                ciFile?.let {
                    val content = VfsUtil.loadText(it)
                    assertTrue("Should run on ubuntu-latest", content.contains("runs-on: ubuntu-latest"))
                    assertTrue("Should contain matrix strategy", content.contains("matrix:"))
                }
            }

            val dependabot = githubDir?.findChild("dependabot.yml")
            assertNotNull("dependabot.yml missing", dependabot)

        } finally {
            settings.state.blenderPath = prevBlenderPath
        }
    }
}