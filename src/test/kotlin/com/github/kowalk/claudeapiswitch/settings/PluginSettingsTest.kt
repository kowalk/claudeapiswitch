package com.github.kowalk.claudeapiswitch.settings

import org.junit.Assert.*
import org.junit.Test

class PluginSettingsTest {

    @Test
    fun testDefaultValues() {
        val state = PluginSettings.State()

        assertEquals(Provider.ANTHROPIC, state.currentProvider)
        assertEquals("deepseek-v4-pro[1m]", state.deepseekModel)
        assertEquals("https://api.deepseek.com/anthropic", state.deepseekBaseUrl)
        assertEquals("deepseek-v4-pro[1m]", state.deepseekOpusModel)
        assertEquals("deepseek-v4-pro[1m]", state.deepseekSonnetModel)
        assertEquals("deepseek-v4-flash", state.deepseekHaikuModel)
        assertEquals("deepseek-v4-flash", state.deepseekSubagentModel)
        assertEquals("max", state.deepseekEffortLevel)
        assertTrue(state.syncToProfile)
        assertEquals("", state.profilePath)
    }

    @Test
    fun testStateCanBeMutated() {
        val state = PluginSettings.State()

        state.currentProvider = Provider.DEEPSEEK
        state.deepseekModel = "custom-model"
        state.deepseekBaseUrl = "https://custom.api.com"
        state.syncToProfile = false
        state.profilePath = "/custom/path"

        assertEquals(Provider.DEEPSEEK, state.currentProvider)
        assertEquals("custom-model", state.deepseekModel)
        assertEquals("https://custom.api.com", state.deepseekBaseUrl)
        assertFalse(state.syncToProfile)
        assertEquals("/custom/path", state.profilePath)
    }

    @Test
    fun testProviderEnumValues() {
        assertEquals(2, Provider.entries.size)
        assertEquals("Anthropic", Provider.ANTHROPIC.displayName)
        assertEquals("DeepSeek", Provider.DEEPSEEK.displayName)
    }
}
