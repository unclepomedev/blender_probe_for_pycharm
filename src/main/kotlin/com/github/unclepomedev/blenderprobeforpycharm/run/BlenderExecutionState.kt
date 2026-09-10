package com.github.unclepomedev.blenderprobeforpycharm.run

/** Common state interface for states that cache Blender execution details. */
interface BlenderExecutionState {
    var cachedBlenderPath: String?
    var cachedAddonName: String?
    var cachedSourceRoot: String?
}
