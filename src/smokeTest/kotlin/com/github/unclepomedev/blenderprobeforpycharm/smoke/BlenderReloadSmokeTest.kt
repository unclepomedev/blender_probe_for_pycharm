package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import com.github.unclepomedev.blenderprobeforpycharm.run.app.*
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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.junit.Assume

/**
 * Launches a real Blender process with a minimal fixture add-on enabled, sends a reload command,
 * and verifies the add-on's register() actually re-runs.
 *
 * Skipped when blup cannot resolve a Blender binary.
 */
class BlenderReloadSmokeTest : BaseBlenderTest() {

    private val addonModuleName = "smoke_addon"
    private var fixtureRoot: Path? = null

    override fun tearDown() {
        try {
            fixtureRoot?.toFile()?.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testReloadReRegistersFixtureAddon() {
        val blenderPath = BlenderSettings.getInstance(project).resolveBlenderPath()
        Assume.assumeTrue("No Blender resolved via blup; skipping.", blenderPath != null)

        val root = Files.createTempDirectory("blender-probe-smoke-addon")
        fixtureRoot = root
        root
            .resolve(addonModuleName)
            .createDirectories()
            .resolve("__init__.py")
            .writeText(
                """
                bl_info = {"name": "Smoke Addon", "blender": (4, 2, 0), "category": "Development"}


                def register():
                    print("[SmokeAddon] register() called")


                def unregister():
                    print("[SmokeAddon] unregister() called")
                """
                    .trimIndent()
            )

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
        // Bypass BlenderProbeUtils' manifest-based detection; point straight at the fixture.
        state.cachedAddonName = addonModuleName
        state.cachedSourceRoot = root.toString()

        val output = StringBuilder()
        val addonEnabled = CountDownLatch(1)
        val addonReloaded = CountDownLatch(1)

        val executionResult = state.execute(executor, BlenderRunner())
        val handler: ProcessHandler = executionResult.processHandler
        handler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    output.append(event.text)
                    if (event.text.contains("Successfully enabled addon: $addonModuleName")) {
                        addonEnabled.countDown()
                    }
                    if (event.text.contains("Re-registered $addonModuleName successfully")) {
                        addonReloaded.countDown()
                    }
                }
            }
        )
        handler.startNotify()

        try {
            assertTrue(
                "Fixture add-on was not enabled within 60s.\n--- output ---\n$output",
                addonEnabled.await(60, TimeUnit.SECONDS),
            )

            sendReload(BlenderProbeManager.activePort!!, addonModuleName)

            assertTrue(
                "Add-on was not re-registered within 10s after reload.\n--- output ---\n$output",
                addonReloaded.await(10, TimeUnit.SECONDS),
            )
        } finally {
            handler.destroyProcess()
            handler.waitFor()
        }
    }

    // Mirrors ReloadAddonAction's wire format: 64-byte length header + JSON body.
    private fun sendReload(port: Int, moduleName: String) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 3_000)
            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            val json = """{"action": "reload", "module_name": "$moduleName"}"""
            val body = json.toByteArray(StandardCharsets.UTF_8)
            writer.write(String.format("%-64s", body.size.toString()))
            writer.write(json)
            writer.flush()
        }
    }
}
