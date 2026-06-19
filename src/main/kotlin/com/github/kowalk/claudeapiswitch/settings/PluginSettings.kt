package com.github.kowalk.claudeapiswitch.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "ClaudeApiSwitch",
    storages = [Storage("claude-api-switch.xml")],
    category = SettingsCategory.TOOLS
)
class PluginSettings : PersistentStateComponent<PluginSettings.State> {

    data class State(
        var currentProvider: Provider = Provider.ANTHROPIC,
        var deepseekModel: String = "deepseek-v4-pro[1m]",
        var deepseekBaseUrl: String = "https://api.deepseek.com/anthropic",
        var deepseekOpusModel: String = "deepseek-v4-pro[1m]",
        var deepseekSonnetModel: String = "deepseek-v4-pro[1m]",
        var deepseekHaikuModel: String = "deepseek-v4-flash",
        var deepseekSubagentModel: String = "deepseek-v4-flash",
        var deepseekEffortLevel: String = "max",
        var syncToProfile: Boolean = true,
        var profilePath: String = ""
    )

    @JvmField
    var appState = State()

    override fun getState(): State = appState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.appState)
    }

    companion object {
        fun getInstance(): PluginSettings =
            ApplicationManager.getApplication().getService(PluginSettings::class.java)
    }
}
