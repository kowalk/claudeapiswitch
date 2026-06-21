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

    @Volatile private var configuredCache: Boolean? = null

    private val DEEPSEEK_ENV_VARS = listOf(
        "ANTHROPIC_BASE_URL", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_MODEL",
        "ANTHROPIC_DEFAULT_OPUS_MODEL", "ANTHROPIC_DEFAULT_SONNET_MODEL",
        "ANTHROPIC_DEFAULT_HAIKU_MODEL", "CLAUDE_CODE_SUBAGENT_MODEL",
        "CLAUDE_CODE_EFFORT_LEVEL"
    )

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
                removeProfileBlock()
                writeProfileUnsetBlock()
            }

            clearDeepSeekProcessEnv()

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
                if (!writeProfileBlock(apiKey)) {
                    ApplicationManager.getApplication().invokeLater { notifyProfileSyncFailed() }
                }
            }

            setDeepSeekProcessEnv(apiKey)

            ApplicationManager.getApplication().invokeLater {
                fireProviderChanged(Provider.DEEPSEEK)
                notifySwitchComplete("DeepSeek")
            }
            logger.info("Switched to DeepSeek API (model: ${settings.appState.deepseekModel})")
        }
    }

    // --- PasswordSafe operations ---

    fun getDeepSeekApiKey(): String? =
        PasswordSafe.instance.getPassword(credentialAttributes)

    fun setDeepSeekApiKey(key: String?) {
        PasswordSafe.instance.setPassword(credentialAttributes, key)
        configuredCache = !key.isNullOrBlank()
    }

    // --- Profile file operations ---

    fun getDefaultProfilePath(): String {
        val home = System.getProperty("user.home") ?: return ""
        val osName = System.getProperty("os.name")?.lowercase(Locale.getDefault()) ?: ""

        return when {
            osName.contains("windows") -> {
                // Prefer PS7 if its profile directory already exists, otherwise fall back to PS5
                val ps7 = "$home\\Documents\\PowerShell\\Microsoft.PowerShell_profile.ps1"
                val ps5 = "$home\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1"
                if (File(ps7).parentFile.exists()) ps7 else ps5
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

    private fun writeProfileBlock(apiKey: String): Boolean {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return true

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
            file.parentFile?.mkdirs()

            val existing = if (file.exists()) file.readText() else ""
            val cleaned = removeMarkerBlock(existing, isPowerShell)
            val updated = if (cleaned.isNotBlank() && !cleaned.endsWith("\n")) {
                "$cleaned\n\n$block\n"
            } else if (cleaned.isNotBlank()) {
                "$cleaned\n$block\n"
            } else {
                "$block\n"
            }

            val tempFile = File("$profilePath.tmp")
            tempFile.writeText(updated)
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.info("Profile block written to $profilePath")
            return true
        } catch (e: Exception) {
            logger.warn("Failed to write profile block to $profilePath: ${e.message}")
            return false
        }
    }

    private fun removeProfileBlock(): Boolean {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return true

        // Clean the primary profile and any sibling profiles that commonly
        // contain leftover env vars (e.g. .profile when .bashrc is primary)
        val pathsToClean = mutableSetOf(profilePath)
        val home = System.getProperty("user.home") ?: ""
        if (home.isNotBlank()) {
            for (sibling in listOf("$home/.profile", "$home/.bash_profile", "$home/.bashrc", "$home/.zshrc")) {
                if (sibling != profilePath && File(sibling).exists()) {
                    pathsToClean.add(sibling)
                }
            }
        }

        var allOk = true
        for (path in pathsToClean) {
            if (!cleanProfileFile(path)) allOk = false
        }
        return allOk
    }

    /**
     * Removes ALL ANTHROPIC_* / CLAUDE_CODE_* assignments from a profile file —
     * both plugin-managed marker blocks and any foreign/unguarded lines.
     */
    private fun cleanProfileFile(path: String): Boolean {
        val isPowerShell = path.endsWith(".ps1", ignoreCase = true)

        try {
            val file = File(path)
            if (!file.exists()) return true

            val content = file.readText()
            val cleaned = stripAllClaudeEnvVars(content, isPowerShell)

            if (cleaned != content) {
                val tempFile = File("$path.tmp")
                tempFile.writeText(cleaned)
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                logger.info("Claude env vars stripped from $path")
            }
            return true
        } catch (e: Exception) {
            logger.warn("Failed to clean $path: ${e.message}", e)
            return false
        }
    }

    /**
     * Strips plugin marker blocks first, then any remaining un-guarded
     * ANTHROPIC_* / CLAUDE_CODE_* export lines.
     */
    internal fun stripAllClaudeEnvVars(content: String, isPowerShell: Boolean): String {
        var cleaned = content

        // 1. Remove plugin-managed marker blocks (both current and old markers)
        cleaned = removeMarkerBlock(cleaned, isPowerShell)

        // 2. Strip any remaining ANTHROPIC_* / CLAUDE_CODE_* lines that were
        //    set outside the plugin's markers (foreign / manual overrides).
        //    Match the whole line (including newline) containing the assignment.
        //    [$] in regex matches a literal $ char (avoids Kotlin template conflict).
        val foreignRegex = if (isPowerShell) {
            Regex("""^[ \t]*[$]env:(ANTHROPIC_\w+|CLAUDE_CODE_\w+)\s*=\s*.*(\r?\n|${'$'})""", RegexOption.MULTILINE)
        } else {
            Regex("""^[ \t]*export\s+(ANTHROPIC_\w+|CLAUDE_CODE_\w+)=.*(\r?\n|${'$'})""", RegexOption.MULTILINE)
        }
        cleaned = foreignRegex.replace(cleaned, "")

        // 3. Collapse runs of blank lines
        cleaned = cleaned.replace(Regex("""\n{3,}"""), "\n\n")

        return cleaned
    }

    internal fun removeMarkerBlock(content: String, isPowerShell: Boolean): String {
        var cleaned = content

        val currentPattern = if (isPowerShell) {
            Regex("""[\r\n]*<# claude-api-switch #>[\s\S]*?<# claude-api-switch-end #>[\r\n]*""")
        } else {
            Regex("""[\r\n]*# claude-api-switch\r?\n[\s\S]*?# claude-api-switch-end\r?\n?""")
        }
        cleaned = cleaned.replace(currentPattern, "")

        // backward compat: pre-rename plugin used "claude-deepseek-switch" markers
        val oldPattern = if (isPowerShell) {
            Regex("""[\r\n]*<# claude-deepseek-switch #>[\s\S]*?<# claude-deepseek-switch-end #>[\r\n]*""")
        } else {
            Regex("""[\r\n]*# claude-deepseek-switch\r?\n[\s\S]*?# claude-deepseek-switch-end\r?\n?""")
        }
        cleaned = cleaned.replace(oldPattern, "")

        return cleaned
    }

    /**
     * Injects DeepSeek config into the IDE's process environment so child
     * processes inherit the settings immediately.
     */
    private fun setDeepSeekProcessEnv(apiKey: String) {
        val settings = PluginSettings.getInstance().appState
        val vars = mapOf(
            "ANTHROPIC_BASE_URL" to settings.deepseekBaseUrl,
            "ANTHROPIC_AUTH_TOKEN" to apiKey,
            "ANTHROPIC_MODEL" to settings.deepseekModel,
            "ANTHROPIC_DEFAULT_OPUS_MODEL" to settings.deepseekOpusModel,
            "ANTHROPIC_DEFAULT_SONNET_MODEL" to settings.deepseekSonnetModel,
            "ANTHROPIC_DEFAULT_HAIKU_MODEL" to settings.deepseekHaikuModel,
            "CLAUDE_CODE_SUBAGENT_MODEL" to settings.deepseekSubagentModel,
            "CLAUDE_CODE_EFFORT_LEVEL" to settings.deepseekEffortLevel
        )
        setProcessEnvVars(vars)
    }

    /**
     * JDK 21+ stores the process environment as `Map<Variable, Value>`, NOT
     * `Map<String, String>`.  Passing raw String keys corrupts the map and
     * crashes every subsequent reader (including the terminal plugin).
     *
     * We construct the proper [java.lang.ProcessEnvironment.Variable] and
     * [java.lang.ProcessEnvironment.Value] wrappers via reflection so the
     * internal map stays well-typed.
     */
    private data class ProcessEnvReflection(
        val theEnvironment: MutableMap<Any, Any>,
        val variableCtor: java.lang.reflect.Constructor<*>,
        val valueCtor: java.lang.reflect.Constructor<*>
    )

    private val processEnvReflection: ProcessEnvReflection? by lazy {
        try {
            val envClass = Class.forName("java.lang.ProcessEnvironment")

            val theEnvField = envClass.getDeclaredField("theEnvironment")
            theEnvField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val theEnv = theEnvField.get(null) as MutableMap<Any, Any>

            // Variable(String) and Value(String) are package-private inner classes
            val variableClass = Class.forName("java.lang.ProcessEnvironment\$Variable")
            val valueClass = Class.forName("java.lang.ProcessEnvironment\$Value")
            val varCtor = variableClass.getDeclaredConstructor(String::class.java)
            val valCtor = valueClass.getDeclaredConstructor(String::class.java)
            varCtor.isAccessible = true
            valCtor.isAccessible = true

            ProcessEnvReflection(theEnv, varCtor, valCtor)
        } catch (e: Exception) {
            logger.warn("Cannot access ProcessEnvironment for env injection (${e.message})", e)
            null
        }
    }

    private fun makeEnvVar(name: String): Any? =
        processEnvReflection?.variableCtor?.newInstance(name)

    private fun makeEnvValue(value: String): Any? =
        processEnvReflection?.valueCtor?.newInstance(value)

    /** Puts entries into the JVM process environment using proper typed wrappers. */
    private fun setProcessEnvVars(vars: Map<String, String>) {
        val r = processEnvReflection ?: return
        try {
            for ((k, v) in vars) {
                val key = makeEnvVar(k) ?: continue
                val value = makeEnvValue(v) ?: continue
                r.theEnvironment[key] = value
            }
            logger.info("Set ${vars.size} env vars in IDE process environment")
        } catch (e: Exception) {
            logger.warn("Could not set process env vars: ${e.message}")
        }
    }

    /**
     * Strips ANTHROPIC_* and CLAUDE_CODE_* variables from the IDE's process
     * environment.  Uses properly-typed Variable wrappers to avoid corrupting
     * the internal map (JDK 21+).
     *
     * This covers direct [ProcessBuilder] children, but does NOT affect PTY
     * terminals.  For PTY terminals the unset block in the shell profile is
     * the only mechanism.
     */
    private fun clearDeepSeekProcessEnv() {
        val r = processEnvReflection ?: return
        try {
            for (name in DEEPSEEK_ENV_VARS) {
                val key = makeEnvVar(name) ?: continue
                r.theEnvironment.remove(key)
            }
            logger.info("Cleared ${DEEPSEEK_ENV_VARS.size} DeepSeek env vars from IDE process environment")
        } catch (e: Exception) {
            logger.warn("Could not modify process environment: ${e.message}")
        }
    }

    /**
     * Writes explicit unset commands into the shell profile so that new
     * terminal shells (which source the profile on startup) can neutralize
     * any ANTHROPIC_* / CLAUDE_CODE_* vars inherited from the parent process.
     */
    private fun writeProfileUnsetBlock() {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return

        val pathsToWrite = mutableSetOf(profilePath)
        val home = System.getProperty("user.home") ?: ""
        if (home.isNotBlank()) {
            for (sibling in listOf("$home/.profile", "$home/.bash_profile", "$home/.bashrc", "$home/.zshrc")) {
                if (File(sibling).exists()) {
                    pathsToWrite.add(sibling)
                }
            }
        }

        for (path in pathsToWrite) {
            writeUnsetBlockToFile(path)
        }
    }

    private fun writeUnsetBlockToFile(path: String) {
        val isPowerShell = path.endsWith(".ps1", ignoreCase = true)

        val block = if (isPowerShell) {
            buildString {
                appendLine("<# claude-api-switch #>")
                DEEPSEEK_ENV_VARS.forEach { appendLine("Remove-Item Env:$it -ErrorAction SilentlyContinue") }
                appendLine("<# claude-api-switch-end #>")
            }
        } else {
            buildString {
                appendLine("# claude-api-switch")
                DEEPSEEK_ENV_VARS.forEach { appendLine("unset $it 2>/dev/null || true") }
                appendLine("# claude-api-switch-end")
            }
        }

        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            val existing = if (file.exists()) file.readText() else ""
            val cleaned = removeMarkerBlock(existing, isPowerShell)
            val updated = if (cleaned.isNotBlank() && !cleaned.endsWith("\n")) {
                "$cleaned\n\n$block\n"
            } else if (cleaned.isNotBlank()) {
                "$cleaned\n$block\n"
            } else {
                "$block\n"
            }
            val tempFile = File("$path.tmp")
            tempFile.writeText(updated)
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.info("Unset block written to $path")
        } catch (e: Exception) {
            logger.warn("Failed to write unset block to $path: ${e.message}")
        }
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
                Regex("""^\s*[$]env:(ANTHROPIC_\w+|CLAUDE_CODE_\w+)\s*=""", RegexOption.MULTILINE)
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

    private fun notifyProfileSyncFailed() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val profilePath = resolveProfilePath()
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ClaudeApiSwitch.Notifications")
            .createNotification(
                "Profile sync failed",
                "Could not update ${File(profilePath).name}. Check file permissions.\n" +
                        "The provider was switched in-IDE only.",
                NotificationType.WARNING
            )
            .notify(project)
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
