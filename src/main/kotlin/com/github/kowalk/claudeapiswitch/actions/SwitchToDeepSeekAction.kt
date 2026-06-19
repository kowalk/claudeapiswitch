package com.github.kowalk.claudeapiswitch.actions

import com.github.kowalk.claudeapiswitch.settings.Provider
import com.github.kowalk.claudeapiswitch.services.ProviderService
import com.github.kowalk.claudeapiswitch.ui.PluginSettingsConfigurable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class SwitchToDeepSeekAction : AnAction(
    "Switch to DeepSeek API",
    "Use DeepSeek API (Anthropic-compatible) for Claude Code",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = ProviderService.getInstance()

        if (!service.isDeepSeekConfigured()) {
            // Open settings page so the user can configure the API key
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, PluginSettingsConfigurable::class.java)
            return
        }

        service.switchToDeepSeek()
    }

    override fun update(e: AnActionEvent) {
        val service = ProviderService.getInstance()
        val current = service.getCurrentProvider()
        e.presentation.isEnabled = current != Provider.DEEPSEEK
    }
}
