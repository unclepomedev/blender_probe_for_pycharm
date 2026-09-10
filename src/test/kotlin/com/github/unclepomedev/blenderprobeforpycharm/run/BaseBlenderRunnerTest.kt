package com.github.unclepomedev.blenderprobeforpycharm.run

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunConfiguration
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunConfigurationType
import com.github.unclepomedev.blenderprobeforpycharm.run.app.BlenderRunner
import com.github.unclepomedev.blenderprobeforpycharm.run.test.BlenderTestConfigurationType
import com.github.unclepomedev.blenderprobeforpycharm.run.test.BlenderTestRunConfiguration
import com.github.unclepomedev.blenderprobeforpycharm.run.test.BlenderTestRunner
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor

class BaseBlenderRunnerTest : BaseBlenderTest() {

    fun testCanRunForBlenderRunner() {
        val runner = BlenderRunner()
        val factory = BlenderRunConfigurationType().configurationFactories[0]
        val appConfig = BlenderRunConfiguration(project, factory, "BlenderRun")

        val testFactory = BlenderTestConfigurationType().configurationFactories[0]
        val testConfig = BlenderTestRunConfiguration(project, testFactory, "BlenderTest")

        assertTrue(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, appConfig))
        assertTrue(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, appConfig))
        assertFalse(runner.canRun("OtherExecutor", appConfig))
        assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, testConfig))
    }

    fun testCanRunForBlenderTestRunner() {
        val runner = BlenderTestRunner()
        val testFactory = BlenderTestConfigurationType().configurationFactories[0]
        val testConfig = BlenderTestRunConfiguration(project, testFactory, "BlenderTest")

        val factory = BlenderRunConfigurationType().configurationFactories[0]
        val appConfig = BlenderRunConfiguration(project, factory, "BlenderRun")

        assertTrue(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, testConfig))
        assertFalse(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, testConfig))
        assertFalse(runner.canRun("OtherExecutor", testConfig))
        assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, appConfig))
    }
}
