package com.github.kowalk.claudeapiswitch.actions

import com.github.kowalk.claudeapiswitch.settings.Provider
import com.github.kowalk.claudeapiswitch.services.ProviderService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class SwitchToAnthropicAction : AnAction(
    "Switch to Anthropic API",
    "Use default Anthropic API for Claude Code",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val service = ProviderService.getInstance()
        service.switchToAnthropic()
    }

    override fun update(e: AnActionEvent) {
        val service = ProviderService.getInstance()
        val current = service.getCurrentProvider()
        e.presentation.isEnabled = current != Provider.ANTHROPIC
    }
}
