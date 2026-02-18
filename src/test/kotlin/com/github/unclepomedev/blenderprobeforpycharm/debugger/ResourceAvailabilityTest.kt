package com.github.unclepomedev.blenderprobeforpycharm.debugger

import com.github.unclepomedev.blenderprobeforpycharm.BaseBlenderTest

class ResourceAvailabilityTest : BaseBlenderTest() {

    fun testRedrawScriptShouldExistInResources() {
        val resourcePath = "python/redraw.py"
        val url = this.javaClass.classLoader.getResource(resourcePath)
        assertNotNull("Fatal: $resourcePath not found! The Force Redraw feature will crash.", url)
        val content = url!!.readText()
        assertTrue("Script content seems wrong", content.contains("bpy.ops.wm.redraw_timer"))
    }
}