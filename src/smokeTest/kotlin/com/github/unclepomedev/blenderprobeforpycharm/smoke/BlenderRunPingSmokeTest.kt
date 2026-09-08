package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager

class BlenderRunPingSmokeTest : BaseSmokeTest() {

    fun testRunConfigurationRespondsToPing() {
        val process = launchBlender(requireBlenderBinary())
        try {
            assertTrue(
                "Blender did not report a probe port within 60s.\n--- output ---\n${process.output}",
                process.await(60) { BlenderProbeManager.activePort != null },
            )
            sendProbeCommand(BlenderProbeManager.activePort!!, """{"action": "ping"}""")
            assertTrue(
                "No pong observed within 30s after sending ping.\n--- output ---\n${process.output}",
                process.awaitOutput("Pong! (Received Ping)", 30),
            )
        } finally {
            process.close()
        }
    }
}
