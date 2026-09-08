package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.run.app.*
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.util.Key
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Launches a real Blender process through BlenderRunningState (the same process-launch code used by
 * the actual run configuration) and verifies the probe protocol round-trips: port announcement ->
 * ping -> pong.
 *
 * Skipped when blup cannot resolve a Blender binary.
 */
class BlenderRunPingSmokeTest : BaseSmokeTest() {

    fun testRunConfigurationRespondsToPing() {
        val blenderPath = requireBlenderBinary()

        BlenderSettings.getInstance(project)
            .loadState(BlenderSettings.State(blenderPath = blenderPath, useFactoryStartup = true))

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
            sendProbeCommand(BlenderProbeManager.activePort!!, """{"action": "ping"}""")
            assertTrue(
                "No pong observed within 10s after sending ping.\n--- output ---\n$output",
                pongReceived.await(10, TimeUnit.SECONDS),
            )
        } finally {
            handler.destroyProcess()
        }
    }
}
