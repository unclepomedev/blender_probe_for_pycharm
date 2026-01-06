package com.github.unclepomedev.blenderprobeforpycharm.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "BlenderProbeSettings",
    storages = [Storage("blender_probe.xml")]
)
class BlenderSettings : PersistentStateComponent<BlenderSettings.State> {

    data class State(
        var blenderPath: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): BlenderSettings = project.service()
    }
}