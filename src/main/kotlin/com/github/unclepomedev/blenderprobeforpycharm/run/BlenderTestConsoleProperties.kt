package com.github.unclepomedev.blenderprobeforpycharm.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator

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
