package com.github.unclepomedev.blenderprobeforpycharm.services

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import java.io.File

class BlenderStubServiceTest : BaseBlenderTest() {

    fun testServiceRetrieval() {
        val service = BlenderStubService.getInstance(project)
        assertNotNull("BlenderStubService should be registered as project service", service)
    }

    fun testEnvironmentPreparerExtractsManifestFiles() {
        val tempDir = FileUtil.createTempDirectory("blender_env_test", null)
        try {
            val indicator = EmptyProgressIndicator()
            val scriptPath = BlenderStubEnvironmentPreparer.prepare(tempDir, indicator)
            val scriptFile = File(scriptPath)
            assertTrue("Main script file should exist", scriptFile.exists())
            assertEquals("generate_stubs.py", scriptFile.name)

            val corePy = File(tempDir, "generator/core.py")
            assertTrue("generator/core.py should exist", corePy.exists())
        } finally {
            FileUtil.delete(tempDir)
        }
    }

    fun testProcessRunnerFailsWhenBinaryNotFound() {
        val nonExistentPath = "/path/to/non_existent_blender_binary"
        val tempDir = FileUtil.createTempDirectory("blender_runner_test", null)
        val outDir = FileUtil.createTempDirectory("blender_runner_out", null)
        try {
            val script = File(tempDir, "script.py").apply { writeText("print('hello')") }
            val indicator = EmptyProgressIndicator()

            assertThrows(ExecutionException::class.java) {
                BlenderStubProcessRunner.run(
                    nonExistentPath,
                    script.absolutePath,
                    outDir,
                    indicator,
                )
            }
        } finally {
            FileUtil.delete(tempDir)
            FileUtil.delete(outDir)
        }
    }
}
