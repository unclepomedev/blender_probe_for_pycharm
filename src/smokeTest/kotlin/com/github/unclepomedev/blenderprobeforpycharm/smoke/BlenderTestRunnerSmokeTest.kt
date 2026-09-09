package com.github.unclepomedev.blenderprobeforpycharm.smoke

import java.nio.file.Files
import java.nio.file.Path

class BlenderTestRunnerSmokeTest : BaseSmokeTest() {

    private val addonModuleName = "smoke_addon"
    private var fixtureRoot: Path? = null

    override fun tearDown() {
        try {
            fixtureRoot?.toFile()?.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testRunnerExecutesTestsSuccessfully() {
        val blenderPath = requireBlenderBinary()
        val root = Files.createTempDirectory("blender-probe-smoke-test-runner")
        fixtureRoot = root

        copyFixtureAddon(root)

        val testsDir = root.resolve("tests")
        copyFixtureResource("/fixtures/test_sample.py", testsDir.resolve("test_sample.py"))

        val process =
            launchBlenderTest(blenderPath, testsDir.toString()) {
                cachedAddonName = addonModuleName
                cachedSourceRoot = root.toString()
            }

        try {
            assertTrue(
                "Add-on auto-registration was not observed within 60s.\n--- output ---\n${process.output}",
                process.awaitOutput("Automatically registered addon package: $addonModuleName", 60),
            )
            assertTrue(
                "TeamCity test suite start was not observed within 60s.\n--- output ---\n${process.output}",
                process.awaitOutput("##teamcity[testSuiteStarted name='Blender Tests']", 60),
            )
            assertTrue(
                "TeamCity test start was not observed within 60s.\n--- output ---\n${process.output}",
                process.awaitOutput("##teamcity[testStarted", 60),
            )
            assertTrue(
                "TeamCity test finish was not observed within 60s.\n--- output ---\n${process.output}",
                process.awaitOutput("##teamcity[testFinished", 60),
            )
            assertTrue(
                "TeamCity test suite finish was not observed within 60s.\n--- output ---\n${process.output}",
                process.awaitOutput("##teamcity[testSuiteFinished name='Blender Tests']", 60),
            )
        } finally {
            process.close()
        }
    }
}
