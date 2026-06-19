package com.github.kowalk.claudeapiswitch.actions

import com.github.kowalk.claudeapiswitch.settings.Provider
import com.github.kowalk.claudeapiswitch.services.ProviderService
import com.github.kowalk.claudeapiswitch.ui.PluginSettingsConfigurable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class ToggleProviderToolbarAction : AnAction(), DumbAware {

    private val service get() = ProviderService.getInstance()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        when (service.getCurrentProvider()) {
            Provider.ANTHROPIC -> {
                if (!service.isDeepSeekConfigured()) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        PluginSettingsConfigurable::class.java
                    )
                    return
                }
                service.switchToDeepSeek()
            }
            Provider.DEEPSEEK -> service.switchToAnthropic()
        }
    }

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        val provider = service.getCurrentProvider()
        val configured = service.isDeepSeekConfigured()

        presentation.icon = when {
            provider == Provider.DEEPSEEK -> deepseekIcon
            !configured -> unconfiguredIcon
            else -> anthropicIcon
        }

        // Only set description (tooltip), not text (which would duplicate menu entries)
        presentation.description = when {
            provider == Provider.DEEPSEEK -> "DeepSeek active — click to switch to Anthropic API"
            !configured -> "DeepSeek not configured — click to open settings"
            else -> "Anthropic active — click to switch to DeepSeek API"
        }
    }

    companion object {
        private val anthropicIcon: Icon by lazy {
            IconLoader.getIcon("/icons/anthropic.svg", ToggleProviderToolbarAction::class.java)
        }
        private val deepseekIcon: Icon by lazy {
            IconLoader.getIcon("/icons/deepseek.svg", ToggleProviderToolbarAction::class.java)
        }
        private val unconfiguredIcon: Icon by lazy {
            IconLoader.getIcon("/icons/unconfigured.svg", ToggleProviderToolbarAction::class.java)
        }
    }
}
