package com.github.kowalk.claudeapiswitch.widget

import com.github.kowalk.claudeapiswitch.settings.Provider
import com.github.kowalk.claudeapiswitch.services.ProviderService
import com.github.kowalk.claudeapiswitch.ui.PluginSettingsConfigurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

class ProviderStatusWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    private val service get() = ProviderService.getInstance()

    override fun ID(): String = "claude-api-switch.widget"

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val provider = service.getCurrentProvider()
        val configured = service.isDeepSeekConfigured()
        val icon = when {
            provider == Provider.DEEPSEEK -> "🔵"  // blue circle
            !configured -> "⚠"                            // warning
            else -> "🟠"                              // orange circle
        }
        return "$icon AI: ${provider.displayName}"
    }

    override fun getTooltipText(): String {
        val provider = service.getCurrentProvider()
        val configured = service.isDeepSeekConfigured()
        return when {
            !configured -> "DeepSeek not configured. Click to open settings."
            provider == Provider.ANTHROPIC -> "Claude Code: Anthropic API. Click to switch to DeepSeek."
            else -> "Claude Code: DeepSeek API. Click to switch to Anthropic."
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        val current = service.getCurrentProvider()
        when (current) {
            Provider.ANTHROPIC -> {
                if (!service.isDeepSeekConfigured()) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        PluginSettingsConfigurable::class.java
                    )
                    return@Consumer
                }
                service.switchToDeepSeek()
            }
            Provider.DEEPSEEK -> service.switchToAnthropic()
        }
    }

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun install(statusBar: StatusBar) {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(
            ProviderService.PROVIDER_CHANGED_TOPIC,
            object : ProviderService.ProviderChangedListener {
                override fun providerChanged(newProvider: Provider) {
                    statusBar.updateWidget(ID())
                }
            }
        )
    }

    override fun dispose() {
    }
}
