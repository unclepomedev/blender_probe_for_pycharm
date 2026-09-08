package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderProbeRunConfigurationFactory
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunConfiguration
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunConfigurationType
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunner
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunningState
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.util.Key
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assume

/**
 * Launches a real Blender process through BlenderRunningState (the same process-launch code used by
 * the actual run configuration) and verifies the probe protocol round-trips: port announcement ->
 * ping -> pong.
 *
 * Skipped when blup cannot resolve a Blender binary.
 */
class BlenderRunPingSmokeTest : BaseBlenderTest() {

    fun testRunConfigurationRespondsToPing() {
        val blenderPath = BlenderSettings.getInstance(project).resolveBlenderPath()
        Assume.assumeTrue("No Blender resolved via blup; skipping.", blenderPath != null)

        BlenderSettings.getInstance(project)
            .loadState(BlenderSettings.State(blenderPath = blenderPath!!, useFactoryStartup = true))

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val configuration =
            BlenderRunConfiguration(
                project,
                BlenderProbeRunConfigurationFactory(BlenderRunConfigurationType()),
                "Smoke",
            )
        val environment =
            ExecutionEnvironmentBuilder(project, executor).runProfile(configuration).build()
        val state = configuration.getState(executor, environment) as BlenderRunningState

        val output = StringBuilder()
        val portReady = CountDownLatch(1)
        val pongReceived = CountDownLatch(1)

        val executionResult = state.execute(executor, BlenderRunner())
        val handler: ProcessHandler = executionResult.processHandler
        handler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    output.append(event.text)
                    if (BlenderProbeManager.activePort != null) portReady.countDown()
                    if (event.text.contains("Pong! (Received Ping)")) pongReceived.countDown()
                }
            }
        )
        handler.startNotify()

        try {
            assertTrue(
                "Blender did not report a probe port within 60s.\n--- output ---\n$output",
                portReady.await(60, TimeUnit.SECONDS),
            )
            sendPing(BlenderProbeManager.activePort!!)
            assertTrue(
                "No pong observed within 10s after sending ping.\n--- output ---\n$output",
                pongReceived.await(10, TimeUnit.SECONDS),
            )
        } finally {
            handler.destroyProcess()
        }
    }

    // Mirrors PingBlenderAction's wire format: 64-byte length header + JSON body.
    private fun sendPing(port: Int) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 3_000)
            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            val json = """{"action": "ping"}"""
            val body = json.toByteArray(StandardCharsets.UTF_8)
            writer.write(String.format("%-64s", body.size.toString()))
            writer.write(json)
            writer.flush()
        }
    }
}
