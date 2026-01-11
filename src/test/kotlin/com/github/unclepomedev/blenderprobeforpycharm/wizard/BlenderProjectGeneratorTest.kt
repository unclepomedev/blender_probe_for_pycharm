package com.github.unclepomedev.blenderprobeforpycharm.wizard

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.util.Locale

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
            assertNotNull("GitIgnore should be created", baseDir.findChild(".gitignore"))

            val expectedSlug = project.name.lowercase(Locale.US).replace(" ", "_").replace("-", "_")
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
            assertNotNull("test_sample.py missing", testsDir?.findChild("test_sample.py"))

        } finally {
            settings.state.blenderPath = prevBlenderPath
        }
    }
}