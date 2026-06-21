package com.github.kowalk.claudeapiswitch.services

import com.github.kowalk.claudeapiswitch.settings.PluginSettings
import com.github.kowalk.claudeapiswitch.settings.Provider
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.messages.Topic
import java.io.File
import java.util.*

@Service(Service.Level.APP)
class ProviderService {

    private val logger = Logger.getInstance(ProviderService::class.java)

    // --- Delegates ---

    private val profileManager by lazy { ShellProfileManager(logger, DEEPSEEK_ENV_VARS) }
    private val envPatcher by lazy { ProcessEnvironmentPatcher(logger) }

    // --- Message bus ---

    interface ProviderChangedListener : EventListener {
        fun providerChanged(newProvider: Provider)
    }

    companion object {
        val PROVIDER_CHANGED_TOPIC = Topic.create(
            "Claude AI Provider Changed",
            ProviderChangedListener::class.java
        )

        fun getInstance(): ProviderService =
            ApplicationManager.getApplication().getService(ProviderService::class.java)
    }

    // --- Shared constants ---

    private val credentialAttributes = CredentialAttributes("ClaudeApiSwitch")

    @Volatile private var configuredCache: Boolean? = null

    private val DEEPSEEK_ENV_VARS = listOf(
        "ANTHROPIC_BASE_URL", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_MODEL",
        "ANTHROPIC_DEFAULT_OPUS_MODEL", "ANTHROPIC_DEFAULT_SONNET_MODEL",
        "ANTHROPIC_DEFAULT_HAIKU_MODEL", "CLAUDE_CODE_SUBAGENT_MODEL",
        "CLAUDE_CODE_EFFORT_LEVEL"
    )

    // ---- Public API ----

    fun getCurrentProvider(): Provider =
        PluginSettings.getInstance().appState.currentProvider

    fun getEnvironmentVariables(): Map<String, String> {
        if (getCurrentProvider() != Provider.DEEPSEEK) return emptyMap()

        val settings = PluginSettings.getInstance().appState
        val apiKey = getDeepSeekApiKey()
        if (apiKey.isNullOrBlank()) {
            logger.warn("DeepSeek provider selected but no API key configured")
            return emptyMap()
        }
        return mapOf(
            "ANTHROPIC_BASE_URL" to settings.deepseekBaseUrl,
            "ANTHROPIC_AUTH_TOKEN" to apiKey,
            "ANTHROPIC_MODEL" to settings.deepseekModel,
            "ANTHROPIC_DEFAULT_OPUS_MODEL" to settings.deepseekOpusModel,
            "ANTHROPIC_DEFAULT_SONNET_MODEL" to settings.deepseekSonnetModel,
            "ANTHROPIC_DEFAULT_HAIKU_MODEL" to settings.deepseekHaikuModel,
            "CLAUDE_CODE_SUBAGENT_MODEL" to settings.deepseekSubagentModel,
            "CLAUDE_CODE_EFFORT_LEVEL" to settings.deepseekEffortLevel
        )
    }

    fun isDeepSeekConfigured(): Boolean {
        val cached = configuredCache
        if (cached != null) return cached
        val result = !getDeepSeekApiKey().isNullOrBlank()
        configuredCache = result
        return result
    }

    fun switchToAnthropic() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val settings = PluginSettings.getInstance()
            settings.appState.currentProvider = Provider.ANTHROPIC

            if (settings.appState.syncToProfile) {
                warnIfForeignEnvVars()
                profileManager.removeExportBlock()
                profileManager.writeUnsetBlock()
            }

            envPatcher.clearVars(DEEPSEEK_ENV_VARS)

            ApplicationManager.getApplication().invokeLater {
                fireProviderChanged(Provider.ANTHROPIC)
                notifySwitchComplete("Anthropic")
            }
            logger.info("Switched to Anthropic API")
        }
    }

    fun switchToDeepSeek() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val apiKey = getDeepSeekApiKey()
            if (apiKey.isNullOrBlank()) {
                logger.warn("Cannot switch to DeepSeek: no API key configured")
                return@executeOnPooledThread
            }

            val settings = PluginSettings.getInstance()
            settings.appState.currentProvider = Provider.DEEPSEEK

            if (settings.appState.syncToProfile) {
                warnIfForeignEnvVars()
                if (!profileManager.writeExportBlock(apiKey)) {
                    ApplicationManager.getApplication().invokeLater { notifyProfileSyncFailed() }
                }
            }

            setProcessEnv(apiKey, settings.appState)

            ApplicationManager.getApplication().invokeLater {
                fireProviderChanged(Provider.DEEPSEEK)
                notifySwitchComplete("DeepSeek")
            }
            logger.info("Switched to DeepSeek API (model: ${settings.appState.deepseekModel})")
        }
    }

    // ---- PasswordSafe ----

    fun getDeepSeekApiKey(): String? =
        PasswordSafe.instance.getPassword(credentialAttributes)

    fun getDefaultProfilePath(): String = profileManager.getDefaultProfilePath()

    fun setDeepSeekApiKey(key: String?) {
        PasswordSafe.instance.setPassword(credentialAttributes, key)
        configuredCache = !key.isNullOrBlank()
    }

    // ---- Process env injection ----

    private fun setProcessEnv(apiKey: String, settings: PluginSettings.State) {
        envPatcher.setVars(
            mapOf(
                "ANTHROPIC_BASE_URL" to settings.deepseekBaseUrl,
                "ANTHROPIC_AUTH_TOKEN" to apiKey,
                "ANTHROPIC_MODEL" to settings.deepseekModel,
                "ANTHROPIC_DEFAULT_OPUS_MODEL" to settings.deepseekOpusModel,
                "ANTHROPIC_DEFAULT_SONNET_MODEL" to settings.deepseekSonnetModel,
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" to settings.deepseekHaikuModel,
                "CLAUDE_CODE_SUBAGENT_MODEL" to settings.deepseekSubagentModel,
                "CLAUDE_CODE_EFFORT_LEVEL" to settings.deepseekEffortLevel
            )
        )
    }

    // ---- Foreign env var warnings ----

    private fun warnIfForeignEnvVars() {
        val foreign = profileManager.findForeignClaudeEnvVars()
        if (foreign.isEmpty()) return

        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val profilePath = PluginSettings.getInstance().appState.profilePath
            .ifBlank { profileManager.getDefaultProfilePath() }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("ClaudeApiSwitch.Notifications")
            .createNotification(
                "Custom Claude Code environment variables detected",
                buildString {
                    append("Custom Claude Code env vars found in ")
                    append(File(profilePath).name)
                    append(":\n")
                    append(foreign.joinToString(", "))
                    append("\n\nThese may conflict with the plugin's settings.")
                    append("\nConsider removing them to avoid unexpected behavior.")
                },
                NotificationType.WARNING
            )
            .notify(project)

        logger.warn("Foreign Claude env vars in $profilePath: ${foreign.joinToString()}")
    }

    // ---- Notifications ----

    private fun notifyProfileSyncFailed() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val name = File(
            PluginSettings.getInstance().appState.profilePath
                .ifBlank { profileManager.getDefaultProfilePath() }
        ).name

        NotificationGroupManager.getInstance()
            .getNotificationGroup("ClaudeApiSwitch.Notifications")
            .createNotification(
                "Profile sync failed",
                "Could not update $name. Check file permissions.\n" +
                        "The provider was switched in-IDE only.",
                NotificationType.WARNING
            )
            .notify(project)
    }

    private fun notifySwitchComplete(providerName: String) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ClaudeApiSwitch.Notifications")
            .createNotification(
                "Switched to $providerName API",
                "Restart any active Claude Code sessions for the change to take effect.",
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    // ---- Message bus ----

    private fun fireProviderChanged(newProvider: Provider) {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(PROVIDER_CHANGED_TOPIC)
            .providerChanged(newProvider)
    }
}
