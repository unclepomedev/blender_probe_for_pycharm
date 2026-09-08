package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import org.junit.Assume

/**
 * Verifies that the Blender binary resolved via blup (BlenderSettings.resolveBlenderPath()) can
 * actually be launched. Skipped if blup or a Blender install is unavailable.
 */
class BlenderBinarySmokeTest : BaseBlenderTest() {

    fun testResolvedBlenderBinaryRuns() {
        val blenderPath = BlenderSettings.getInstance(project).resolveBlenderPath()
        Assume.assumeTrue("No Blender resolved via blup; skipping.", blenderPath != null)

        val commandLine = GeneralCommandLine(blenderPath, "--background", "--version")
        val output = CapturingProcessHandler(commandLine).runProcess(30_000)

        assertEquals(
            "blender --background --version exited non-zero.\n" +
                "--- stdout ---\n${output.stdout}\n--- stderr ---\n${output.stderr}",
            0,
            output.exitCode,
        )
        assertTrue(
            "Version output did not contain \"Blender\".\n${output.stdout}",
            output.stdout.contains("Blender"),
        )
    }
}
