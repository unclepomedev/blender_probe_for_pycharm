package com.github.unclepomedev.blenderprobeforpycharm.run.app

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.io.FileUtil
import java.io.File

class ProbeProcessListenerTest : BaseBlenderTest() {

    fun testProcessTerminated_DeletesTempDirAndClearsPort() {
        val tempDir = FileUtil.createTempDirectory("blender_probe_test", null)
        File(tempDir, "probe_server.py").writeText("# dummy")
        BlenderProbeManager.activePort = 12345

        ProbeProcessListener(tempDir).processTerminated(ProcessEvent(NopProcessHandler()))

        assertFalse("temp dir should be deleted", tempDir.exists())
        assertNull("active port should be cleared", BlenderProbeManager.activePort)
    }
}
