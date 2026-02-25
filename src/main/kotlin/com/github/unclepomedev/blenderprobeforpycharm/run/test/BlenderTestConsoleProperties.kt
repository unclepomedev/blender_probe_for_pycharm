package com.github.unclepomedev.blenderprobeforpycharm.run.test

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator

/**
 * Console properties for the Blender Test runner.
 * Configures the test runner console behavior.
 */
class BlenderTestConsoleProperties(
    config: RunConfiguration,
    executor: Executor
) : SMTRunnerConsoleProperties(config, "BlenderTest", executor) {

    init {
        isUsePredefinedMessageFilter = false
    }

    override fun getTestLocator(): SMTestLocator? {
        return null
    }
}
