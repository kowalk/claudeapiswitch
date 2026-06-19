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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*

@Service(Service.Level.APP)
class ProviderService {

    private val logger = Logger.getInstance(ProviderService::class.java)

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

    // --- Credential attributes for PasswordSafe ---

    private val credentialAttributes = CredentialAttributes("ClaudeApiSwitch")

    // --- Public API ---

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

    fun isDeepSeekConfigured(): Boolean =
        !getDeepSeekApiKey().isNullOrBlank()

    fun switchToAnthropic() {
        val settings = PluginSettings.getInstance()
        settings.appState.currentProvider = Provider.ANTHROPIC

        if (settings.appState.syncToProfile) {
            warnIfForeignEnvVars()
            removeProfileBlock()
        }

        fireProviderChanged(Provider.ANTHROPIC)
        notifySwitchComplete("Anthropic")
        logger.info("Switched to Anthropic API")
    }

    fun switchToDeepSeek(): Boolean {
        val settings = PluginSettings.getInstance()
        val apiKey = getDeepSeekApiKey()

        if (apiKey.isNullOrBlank()) {
            logger.warn("Cannot switch to DeepSeek: no API key configured")
            return false
        }

        settings.appState.currentProvider = Provider.DEEPSEEK

        if (settings.appState.syncToProfile) {
            warnIfForeignEnvVars()
            writeProfileBlock(apiKey)
        }

        fireProviderChanged(Provider.DEEPSEEK)
        notifySwitchComplete("DeepSeek")
        logger.info("Switched to DeepSeek API (model: ${settings.appState.deepseekModel})")
        return true
    }

    // --- PasswordSafe operations ---

    fun getDeepSeekApiKey(): String? =
        PasswordSafe.instance.getPassword(credentialAttributes)

    fun setDeepSeekApiKey(key: String) {
        PasswordSafe.instance.setPassword(credentialAttributes, key)
    }

    // --- Profile file operations ---

    fun getDefaultProfilePath(): String {
        val home = System.getProperty("user.home") ?: return ""
        val osName = System.getProperty("os.name")?.lowercase(Locale.getDefault()) ?: ""

        return when {
            osName.contains("windows") -> {
                // PowerShell 7+ default profile (modern Windows)
                "$home\\Documents\\PowerShell\\Microsoft.PowerShell_profile.ps1"
            }
            else -> {
                // .bashrc is sourced by interactive shells on Linux; .profile is login-only
                "$home/.bashrc"
            }
        }
    }

    private fun resolveProfilePath(): String {
        val settings = PluginSettings.getInstance().appState
        return settings.profilePath.ifBlank { getDefaultProfilePath() }
    }

    private fun writeProfileBlock(apiKey: String) {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return

        val settings = PluginSettings.getInstance().appState
        val isPowerShell = profilePath.endsWith(".ps1", ignoreCase = true)

        val block = if (isPowerShell) {
            buildString {
                appendLine("<# claude-api-switch #>")
                appendLine("\$env:ANTHROPIC_BASE_URL = \"${settings.deepseekBaseUrl}\"")
                appendLine("\$env:ANTHROPIC_AUTH_TOKEN = \"$apiKey\"")
                appendLine("\$env:ANTHROPIC_MODEL = \"${settings.deepseekModel}\"")
                appendLine("\$env:ANTHROPIC_DEFAULT_OPUS_MODEL = \"${settings.deepseekOpusModel}\"")
                appendLine("\$env:ANTHROPIC_DEFAULT_SONNET_MODEL = \"${settings.deepseekSonnetModel}\"")
                appendLine("\$env:ANTHROPIC_DEFAULT_HAIKU_MODEL = \"${settings.deepseekHaikuModel}\"")
                appendLine("\$env:CLAUDE_CODE_SUBAGENT_MODEL = \"${settings.deepseekSubagentModel}\"")
                appendLine("\$env:CLAUDE_CODE_EFFORT_LEVEL = \"${settings.deepseekEffortLevel}\"")
                appendLine("<# claude-api-switch-end #>")
            }
        } else {
            buildString {
                appendLine("# claude-api-switch")
                appendLine("export ANTHROPIC_BASE_URL=\"${settings.deepseekBaseUrl}\"")
                appendLine("export ANTHROPIC_AUTH_TOKEN=\"$apiKey\"")
                appendLine("export ANTHROPIC_MODEL=\"${settings.deepseekModel}\"")
                appendLine("export ANTHROPIC_DEFAULT_OPUS_MODEL=\"${settings.deepseekOpusModel}\"")
                appendLine("export ANTHROPIC_DEFAULT_SONNET_MODEL=\"${settings.deepseekSonnetModel}\"")
                appendLine("export ANTHROPIC_DEFAULT_HAIKU_MODEL=\"${settings.deepseekHaikuModel}\"")
                appendLine("export CLAUDE_CODE_SUBAGENT_MODEL=\"${settings.deepseekSubagentModel}\"")
                appendLine("export CLAUDE_CODE_EFFORT_LEVEL=\"${settings.deepseekEffortLevel}\"")
                appendLine("# claude-api-switch-end")
            }
        }

        try {
            val file = File(profilePath)
            // Ensure parent directory exists
            file.parentFile?.mkdirs()

            // Remove any existing marker block, then append the new one
            val existing = if (file.exists()) file.readText() else ""
            val cleaned = removeMarkerBlock(existing, isPowerShell)
            val updated = if (cleaned.isNotBlank() && !cleaned.endsWith("\n")) {
                "$cleaned\n\n$block\n"
            } else if (cleaned.isNotBlank()) {
                "$cleaned\n$block\n"
            } else {
                "$block\n"
            }

            // Atomic write: write to temp file then rename
            val tempFile = File("$profilePath.tmp")
            tempFile.writeText(updated)
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.REPLACE_EXISTING
            )
            logger.info("Profile block written to $profilePath")
        } catch (e: Exception) {
            logger.warn("Failed to write profile block to $profilePath: ${e.message}")
        }
    }

    private fun removeProfileBlock() {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return

        val isPowerShell = profilePath.endsWith(".ps1", ignoreCase = true)

        try {
            val file = File(profilePath)
            if (!file.exists()) return

            val content = file.readText()
            val cleaned = removeMarkerBlock(content, isPowerShell)

            if (cleaned != content) {
                // Atomic write
                val tempFile = File("$profilePath.tmp")
                tempFile.writeText(cleaned)
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.REPLACE_EXISTING
                )
                logger.info("Profile block removed from $profilePath")
            }
        } catch (e: Exception) {
            logger.warn("Failed to remove profile block from $profilePath: ${e.message}")
        }
    }

    internal fun removeMarkerBlock(content: String, isPowerShell: Boolean): String {
        var cleaned = content

        // Remove current-marker blocks
        val currentPattern = if (isPowerShell) {
            Regex("""[\r\n]*<# claude-api-switch #>[\s\S]*?<# claude-api-switch-end #>[\r\n]*""")
        } else {
            Regex("""[\r\n]*# claude-api-switch\n[\s\S]*?# claude-api-switch-end\n?""")
        }
        cleaned = cleaned.replace(currentPattern, "")

        // Also remove old-marker blocks (backward compat with pre-rename plugin)
        val oldPattern = if (isPowerShell) {
            Regex("""[\r\n]*<# claude-deepseek-switch #>[\s\S]*?<# claude-deepseek-switch-end #>[\r\n]*""")
        } else {
            Regex("""[\r\n]*# claude-deepseek-switch\n[\s\S]*?# claude-deepseek-switch-end\n?""")
        }
        cleaned = cleaned.replace(oldPattern, "")

        return cleaned
    }

    // --- Foreign env var detection ---

    /**
     * Scans the profile file for ANTHROPIC_* / CLAUDE_CODE_* env vars
     * that are outside the plugin's marker blocks. Returns a list of
     * the offending variable names found.
     */
    private fun findForeignClaudeEnvVars(): List<String> {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return emptyList()

        try {
            val file = File(profilePath)
            if (!file.exists()) return emptyList()

            val content = file.readText()
            val isPowerShell = profilePath.endsWith(".ps1", ignoreCase = true)

            // Remove all plugin-managed blocks (both old and new markers)
            val cleaned = removeMarkerBlock(content, isPowerShell)

            // Look for ANTHROPIC_* or CLAUDE_CODE_* assignments outside our blocks
            val foreignVars = mutableListOf<String>()
            val envRegex = if (isPowerShell) {
                Regex("""^\s*${'$'}env:(ANTHROPIC_\w+|CLAUDE_CODE_\w+)\s*=""", RegexOption.MULTILINE)
            } else {
                Regex("""^\s*export\s+(ANTHROPIC_\w+|CLAUDE_CODE_\w+)=""", RegexOption.MULTILINE)
            }

            envRegex.findAll(cleaned).forEach { match ->
                foreignVars.add(match.groupValues[1])
            }

            return foreignVars
        } catch (e: Exception) {
            logger.warn("Failed to scan profile for foreign env vars: ${e.message}")
            return emptyList()
        }
    }

    private fun warnIfForeignEnvVars() {
        val foreign = findForeignClaudeEnvVars()
        if (foreign.isEmpty()) return

        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val profilePath = resolveProfilePath()

        val message = buildString {
            append("Custom Claude Code env vars found in ")
            append(File(profilePath).name)
            append(":\n")
            append(foreign.joinToString(", "))
            append("\n\nThese may conflict with the plugin's settings.")
            append("\nConsider removing them to avoid unexpected behavior.")
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("ClaudeApiSwitch.Notifications")
            .createNotification(
                "Custom Claude Code environment variables detected",
                message,
                NotificationType.WARNING
            )
        notification.notify(project)
        logger.warn("Foreign Claude env vars in $profilePath: ${foreign.joinToString()}")
    }

    private fun notifySwitchComplete(providerName: String) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("ClaudeApiSwitch.Notifications")
            .createNotification(
                "Switched to $providerName API",
                "Restart any active Claude Code sessions for the change to take effect.",
                NotificationType.INFORMATION
            )
        notification.notify(project)
    }

    // --- Message bus ---

    private fun fireProviderChanged(newProvider: Provider) {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(PROVIDER_CHANGED_TOPIC)
            .providerChanged(newProvider)
    }
}
