package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler

/**
 * Verifies that the Blender binary resolved via blup (BlenderSettings.resolveBlenderPath()) can
 * actually be launched. Skipped if blup or a Blender install is unavailable.
 */
class BlenderBinarySmokeTest : BaseSmokeTest() {

    fun testResolvedBlenderBinaryRuns() {
        val blenderPath = requireBlenderBinary()

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
