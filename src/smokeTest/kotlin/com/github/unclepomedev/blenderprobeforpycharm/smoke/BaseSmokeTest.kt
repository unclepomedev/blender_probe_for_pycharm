package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.probe.BlenderProbeClient
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderProbeRunConfigurationFactory
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunConfiguration
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunConfigurationType
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunner
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunningState
import com.github.unclepomedev.blenderprobeforpycharm.run.test.BlenderTestConfigurationFactory
import com.github.unclepomedev.blenderprobeforpycharm.run.test.BlenderTestConfigurationType
import com.github.unclepomedev.blenderprobeforpycharm.run.test.BlenderTestRunConfiguration
import com.github.unclepomedev.blenderprobeforpycharm.run.test.BlenderTestRunner
import com.github.unclepomedev.blenderprobeforpycharm.run.test.BlenderTestRunningState
import com.github.unclepomedev.blenderprobeforpycharm.settings.BlenderSettings
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import org.junit.Assume

/** Shared helpers for smoke tests that launch a real Blender process via blup. */
abstract class BaseSmokeTest : BaseBlenderTest() {

    /** Resolves the Blender binary via blup, skipping the test if none is installed. */
    protected fun requireBlenderBinary(): String {
        val path = BlenderSettings.getInstance(project).resolveBlenderPath()
        Assume.assumeTrue("No Blender resolved via blup; skipping.", path != null)
        return path!!
    }

    /**
     * Launches a real Blender process through BlenderRunningState (the same process-launch code
     * used by the actual run configuration).
     */
    protected fun launchBlender(
        blenderPath: String,
        configureState: BlenderRunningState.() -> Unit = {},
    ): RunningBlenderProcess {
        val configuration =
            BlenderRunConfiguration(
                project,
                BlenderProbeRunConfigurationFactory(BlenderRunConfigurationType()),
                "Smoke",
            )
        return launchProcess(blenderPath, configuration, BlenderRunner(), configureState)
    }

    /**
     * Launches a real Blender process through BlenderTestRunningState (the process-launch code used
     * by BlenderTestRunner and BlenderTestRunConfiguration).
     */
    protected fun launchBlenderTest(
        blenderPath: String,
        testDir: String,
        configureState: BlenderTestRunningState.() -> Unit = {},
    ): RunningBlenderProcess {
        val configuration =
            BlenderTestRunConfiguration(
                    project,
                    BlenderTestConfigurationFactory(BlenderTestConfigurationType()),
                    "SmokeTest",
                )
                .apply {
                    this.testDir = testDir
                }
        return launchProcess(blenderPath, configuration, BlenderTestRunner(), configureState)
    }

    private inline fun <reified S : RunProfileState> launchProcess(
        blenderPath: String,
        configuration: RunProfile,
        runner: ProgramRunner<*>,
        configureState: S.() -> Unit,
    ): RunningBlenderProcess {
        BlenderSettings.getInstance(project)
            .loadState(BlenderSettings.State(blenderPath = blenderPath, useFactoryStartup = true))

        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment =
            ExecutionEnvironmentBuilder(project, executor).runProfile(configuration).build()
        val state = (configuration.getState(executor, environment) as S).apply(configureState)

        val output = StringBuffer()
        val executionResult =
            state.execute(executor, runner) ?: error("ExecutionResult must not be null")
        val handler = executionResult.processHandler
        handler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    output.append(event.text)
                }
            }
        )
        handler.startNotify()
        return RunningBlenderProcess(handler, output, executionResult.executionConsole)
    }

    /** Sends a probe command using the same wire format as PingBlenderAction/ReloadAddonAction. */
    protected fun sendProbeCommand(port: Int, json: String) {
        BlenderProbeClient.sendCommand(port, json)
    }

    /** Copies a resource file from classpath to the target file path. */
    protected fun copyFixtureResource(resourcePath: String, targetPath: Path) {
        val input =
            javaClass.getResourceAsStream(resourcePath)
                ?: error("Fixture resource not found on classpath: $resourcePath")
        targetPath.parent?.createDirectories()
        input.use { Files.copy(it, targetPath) }
    }

    /** Copies the fixture smoke addon module into targetRoot. */
    protected fun copyFixtureAddon(targetRoot: Path, addonModuleName: String = "smoke_addon") {
        val resourcePath = "/fixtures/smoke-addon/$addonModuleName/__init__.py"
        val target = targetRoot.resolve(addonModuleName).resolve("__init__.py")
        copyFixtureResource(resourcePath, target)
    }

    /** A launched Blender process plus its accumulated stdout/stderr. */
    protected class RunningBlenderProcess(
        private val handler: ProcessHandler,
        val output: StringBuffer,
        private val console: ExecutionConsole? = null,
    ) {
        fun await(timeoutSeconds: Long, condition: () -> Boolean): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (!condition()) {
                if (System.nanoTime() >= deadline) return false
                Thread.sleep(50)
            }
            return true
        }

        fun awaitOutput(marker: String, timeoutSeconds: Long): Boolean =
            await(timeoutSeconds) { output.contains(marker) }

        fun close() {
            try {
                handler.destroyProcess()
                if (!handler.waitFor(10_000)) {
                    error("Blender process did not terminate within 10s after destroyProcess")
                }
            } finally {
                (console as? Disposable)?.let {
                    Disposer.dispose(it)
                }
            }
        }
    }
}
