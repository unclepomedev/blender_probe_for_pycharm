package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.services.BlenderStubService
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import java.io.File

class BlenderStubServiceSmokeTest : BaseSmokeTest() {

    fun testPrepareGeneratorEnvironmentExtractsScripts() {
        val tempDir = FileUtil.createTempDirectory("blender_stub_test", null)
        try {
            val service = BlenderStubService.getInstance(project)
            val indicator = EmptyProgressIndicator()
            val scriptPath = service.prepareGeneratorEnvironment(tempDir, indicator)

            val scriptFile = File(scriptPath)
            assertTrue("generate_stubs.py does not exist at $scriptPath", scriptFile.exists())
            assertEquals("generate_stubs.py", scriptFile.name)

            val generatorCore = File(tempDir, "generator/core.py")
            assertTrue(
                "generator/core.py does not exist at ${generatorCore.path}",
                generatorCore.exists(),
            )
        } finally {
            FileUtil.delete(tempDir)
        }
    }

    fun testRunBlenderProcessGeneratesStubs() {
        val blenderPath = requireBlenderBinary()
        val tempDir = FileUtil.createTempDirectory("blender_stub_env", null)
        val outputDir = FileUtil.createTempDirectory("blender_stubs_out", null)
        try {
            val service = BlenderStubService.getInstance(project)
            val indicator = EmptyProgressIndicator()
            val scriptPath = service.prepareGeneratorEnvironment(tempDir, indicator)

            val stdout = service.runBlenderProcess(blenderPath, scriptPath, outputDir, indicator)

            assertTrue(
                "Process output did not indicate success.\n$stdout",
                stdout.contains("All stubs generated successfully."),
            )

            val bpyDir = File(outputDir, "bpy")
            assertTrue(
                "bpy directory does not exist in $outputDir",
                bpyDir.exists() && bpyDir.isDirectory,
            )

            val bpyInit = File(bpyDir, "__init__.pyi")
            assertTrue("__init__.pyi does not exist in $bpyDir", bpyInit.exists() && bpyInit.isFile)
            assertTrue("__init__.pyi is empty", bpyInit.length() > 0)
        } finally {
            FileUtil.delete(tempDir)
            FileUtil.delete(outputDir)
        }
    }
}
