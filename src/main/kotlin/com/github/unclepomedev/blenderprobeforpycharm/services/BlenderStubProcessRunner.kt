package com.github.unclepomedev.blenderprobeforpycharm.services

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import java.io.File
import java.nio.charset.StandardCharsets

/** Handles executing the Blender process to generate Python stubs. */
internal object BlenderStubProcessRunner {

    const val PROCESS_TIMEOUT_MS = 5 * 60 * 1000L

    /**
     * Executes Blender with the stub generator script.
     *
     * @param blenderPath Path to the Blender executable.
     * @param scriptPath Path to the generate_stubs.py script.
     * @param outputDir Target directory where stubs should be generated.
     * @param indicator Progress indicator for cancellation and status display.
     * @return Standard output of the Blender process.
     * @throws ExecutionException If the Blender binary does not exist or is invalid.
     * @throws ProcessCanceledException If the task is cancelled by user.
     * @throws RuntimeException If the process times out or exits with non-zero exit code.
     */
    fun run(
        blenderPath: String,
        scriptPath: String,
        outputDir: File,
        indicator: ProgressIndicator,
    ): String {
        validateBlenderExecutable(blenderPath)

        indicator.text = "Running blender..."
        val commandLine = buildCommandLine(blenderPath, scriptPath, outputDir)
        val handler = CapturingProcessHandler(commandLine)

        handler.addProcessListener(createProcessListener(indicator))

        val output = handler.runProcessWithProgressIndicator(indicator, PROCESS_TIMEOUT_MS.toInt())

        if (indicator.isCanceled) {
            throw ProcessCanceledException()
        }

        if (output.isTimeout) {
            throw RuntimeException("Blender process timed out.")
        }

        if (output.exitCode != 0) {
            val errorMsg = output.stderr.ifBlank { output.stdout }
            throw RuntimeException(
                "Blender exited with code ${output.exitCode}.\nError Details:\n$errorMsg"
            )
        }

        return output.stdout
    }

    private fun validateBlenderExecutable(blenderPath: String) {
        val blenderExe = File(blenderPath)
        if (!blenderExe.exists() || !blenderExe.isFile || !blenderExe.canExecute()) {
            throw ExecutionException(
                "Blender executable not found or not executable at: $blenderPath"
            )
        }
    }

    private fun buildCommandLine(
        blenderPath: String,
        scriptPath: String,
        outputDir: File,
    ): GeneralCommandLine {
        return GeneralCommandLine(
                blenderPath,
                "--factory-startup",
                "-b",
                "-P",
                scriptPath,
                "--",
                "--output",
                outputDir.absolutePath,
            )
            .apply {
                charset = StandardCharsets.UTF_8
                environment["PYTHONUNBUFFERED"] = "1"
            }
    }

    private fun createProcessListener(indicator: ProgressIndicator): ProcessListener {
        return object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = event.text
                print("[Blender Stream] $text")

                if (outputType != ProcessOutputTypes.STDERR) {
                    val cleanText = text.trim()
                    if (cleanText.isNotEmpty()) {
                        indicator.text2 = cleanText
                    }
                }
            }

            override fun startNotified(event: ProcessEvent) {}

            override fun processTerminated(event: ProcessEvent) {}
        }
    }
}
