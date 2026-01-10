package com.github.unclepomedev.blenderprobeforpycharm

import java.io.IOException

class ScriptResourceUtilsTest : BaseBlenderTest() {
    companion object {
        private const val VALID_SCRIPT_NAME = "generate_stubs.py"
        private const val INVALID_SCRIPT_NAME = "non_existent_ghost_script.py"
    }

    fun testExtractScriptToTemp_Success() {
        val extractedFile = ScriptResourceUtils.extractScriptToTemp(VALID_SCRIPT_NAME)

        assertNotNull("returned file should not be null", extractedFile)
        assertTrue("extracted file should be physically present", extractedFile.exists())
        assertEquals("filename should match", VALID_SCRIPT_NAME, extractedFile.name)
        assertTrue("file size should be greater than 0", extractedFile.length() > 0)

        val content = extractedFile.readText()
        assertTrue("should include import bpy", content.contains("import bpy"))
    }

    fun testExtractScriptToTemp_NotFound() {
        try {
            ScriptResourceUtils.extractScriptToTemp(INVALID_SCRIPT_NAME)
            fail("should throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Script not found"))
        }
    }

    fun testExtractResourceScript_Success() {
        val resourcePath = "/python/$VALID_SCRIPT_NAME"

        val extractedFile = ScriptResourceUtils.extractResourceScript(resourcePath, "temp_prefix_")

        assertNotNull(extractedFile)
        assertTrue(extractedFile.exists())
        assertTrue("should have the prefix", extractedFile.name.startsWith("temp_prefix_"))
        assertTrue("the extension should be .py", extractedFile.name.endsWith(".py"))
    }

    fun testExtractResourceScript_NotFound() {
        try {
            ScriptResourceUtils.extractResourceScript("/invalid/path/to/resource.py", "foo")
            fail("expect IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Resource not found") ?: false)
        }
    }
}