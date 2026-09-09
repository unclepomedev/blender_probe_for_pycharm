package com.github.unclepomedev.blenderprobeforpycharm.actions

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReloadAddonActionTest : BaseBlenderTest() {

    fun testReloadWithoutManifestSendsFallbackModuleName() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        BlenderProbeManager.activePort = port

        val latch = CountDownLatch(1)
        var receivedCommand: String? = null

        val serverThread = Thread {
            try {
                val clientSocket: Socket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val headerBuf = CharArray(64)
                reader.read(headerBuf)
                val len = String(headerBuf).trim().toInt()
                val bodyBuf = CharArray(len)
                reader.read(bodyBuf)
                receivedCommand = String(bodyBuf)

                val writer = OutputStreamWriter(clientSocket.getOutputStream())
                writer.write("ACK")
                writer.flush()
                clientSocket.close()
            } catch (_: Exception) {} finally {
                latch.countDown()
            }
        }
        serverThread.start()

        try {
            val action = ReloadAddonAction()
            val event =
                AnActionEvent.createEvent(
                    action,
                    { dataId ->
                        if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
                    },
                    Presentation(),
                    "test",
                    ActionUiKind.NONE,
                    null,
                )

            action.actionPerformed(event)

            val received = latch.await(5, TimeUnit.SECONDS)
            assertTrue("Expected server to receive reload command within timeout", received)
            assertNotNull(receivedCommand)
            assertTrue(
                "Command should contain reload action and fallback module_name: $receivedCommand",
                receivedCommand!!.contains(""""action": "reload""""),
            )
        } finally {
            BlenderProbeManager.activePort = null
            serverSocket.close()
            serverThread.join(2000)
        }
    }
}
