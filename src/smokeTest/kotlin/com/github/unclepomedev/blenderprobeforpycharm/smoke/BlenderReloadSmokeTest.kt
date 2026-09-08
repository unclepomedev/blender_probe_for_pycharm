package com.github.unclepomedev.blenderprobeforpycharm.smoke

import com.github.unclepomedev.blenderprobeforpycharm.BlenderProbeManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

class BlenderReloadSmokeTest : BaseSmokeTest() {

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
        val blenderPath = requireBlenderBinary()
        val root = Files.createTempDirectory("blender-probe-smoke-addon")
        fixtureRoot = root
        copyFixtureAddon(root)

        val process =
            launchBlender(blenderPath) {
                // Bypass BlenderProbeUtils' manifest-based detection.
                cachedAddonName = addonModuleName
                cachedSourceRoot = root.toString()
            }

        try {
            assertTrue(
                "Fixture add-on was not enabled within 60s.\n--- output ---\n${process.output}",
                process.awaitOutput("Successfully enabled addon: $addonModuleName", 60),
            )

            sendProbeCommand(
                BlenderProbeManager.activePort!!,
                """{"action": "reload", "module_name": "$addonModuleName"}""",
            )

            assertTrue(
                "Add-on was not re-registered within 30s after reload.\n--- output ---\n${process.output}",
                process.awaitOutput("Re-registered $addonModuleName successfully", 30),
            )
        } finally {
            process.close()
        }
    }

    private fun copyFixtureAddon(targetRoot: Path) {
        val resourcePath = "/fixtures/smoke-addon/$addonModuleName/__init__.py"
        val input =
            javaClass.getResourceAsStream(resourcePath)
                ?: error("Fixture resource not found on classpath: $resourcePath")
        val target = targetRoot.resolve(addonModuleName).createDirectories().resolve("__init__.py")
        input.use { Files.copy(it, target) }
    }
}
