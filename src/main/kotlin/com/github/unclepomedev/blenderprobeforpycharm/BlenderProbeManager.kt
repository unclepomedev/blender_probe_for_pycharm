package com.github.unclepomedev.blenderprobeforpycharm

object BlenderProbeManager {
    var activePort: Int? = null

    fun updatePort(port: Int) {
        activePort = port
        println("Blender Probe: Port updated to $port")
    }
}