package com.github.kowalk.claudeapiswitch.services

import com.github.kowalk.claudeapiswitch.settings.PluginSettings
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Manages shell profile files — path detection, writing/removing export and
 * unset blocks, cleaning foreign env vars, and text-level processing of
 * profile content.
 */
class ShellProfileManager(
    private val logger: Logger,
    private val deepseekEnvVars: List<String>
) {

    // ---- Public path helpers ----

    fun getDefaultProfilePath(): String {
        val home = System.getProperty("user.home") ?: return ""
        val osName = System.getProperty("os.name")?.lowercase(Locale.getDefault()) ?: ""

        return when {
            osName.contains("windows") -> {
                val ps7 = "$home\\Documents\\PowerShell\\Microsoft.PowerShell_profile.ps1"
                val ps5 = "$home\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1"
                if (File(ps7).parentFile.exists()) ps7 else ps5
            }
            else -> "$home/.bashrc"
        }
    }

    // ---- Private helpers ----

    private fun getSiblingProfilePaths(): List<String> {
        val home = System.getProperty("user.home") ?: return emptyList()
        val osName = System.getProperty("os.name")?.lowercase(Locale.getDefault()) ?: ""

        return if (osName.contains("windows")) {
            listOf(
                "$home\\Documents\\PowerShell\\Microsoft.PowerShell_profile.ps1",
                "$home\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1"
            )
        } else {
            listOf("$home/.profile", "$home/.bash_profile", "$home/.bashrc", "$home/.zshrc")
        }
    }

    private fun resolveProfilePath(): String {
        val settings = PluginSettings.getInstance().appState
        return settings.profilePath.ifBlank { getDefaultProfilePath() }
    }

    private fun isPowerShell(path: String) = path.endsWith(".ps1", ignoreCase = true)

    // ---- Write export block ----

    fun writeExportBlock(apiKey: String): Boolean {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return true

        val settings = PluginSettings.getInstance().appState
        val powershell = isPowerShell(profilePath)
        val block = buildExportBlock(settings, apiKey, powershell)

        return writeAtomic(profilePath, block, powershell).also { ok ->
            if (ok) logger.info("Profile block written to $profilePath")
            else logger.warn("Failed to write profile block to $profilePath")
        }
    }

    private fun buildExportBlock(
        settings: PluginSettings.State,
        apiKey: String,
        powershell: Boolean
    ): String = buildString {
        if (powershell) {
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
        } else {
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

    // ---- Remove export block ----

    fun removeExportBlock(): Boolean {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return true

        val paths = collectExistingSiblings(profilePath)
        var allOk = true
        for (path in paths) {
            if (!cleanProfileFile(path)) allOk = false
        }
        return allOk
    }

    private fun collectExistingSiblings(primary: String): Set<String> {
        val result = mutableSetOf(primary)
        for (sibling in getSiblingProfilePaths()) {
            if (sibling != primary && File(sibling).exists()) {
                result.add(sibling)
            }
        }
        return result
    }

    private fun cleanProfileFile(path: String): Boolean {
        try {
            val file = File(path)
            if (!file.exists()) return true

            val content = file.readText()
            val cleaned = stripAllClaudeEnvVars(content, isPowerShell(path))

            if (cleaned != content) {
                val tmp = File("$path.tmp")
                tmp.writeText(cleaned)
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                logger.info("Claude env vars stripped from $path")
            }
            return true
        } catch (e: Exception) {
            logger.warn("Failed to clean $path: ${e.message}", e)
            return false
        }
    }

    // ---- Write unset block ----

    fun writeUnsetBlock() {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return

        val paths = mutableSetOf(profilePath)
        for (sibling in getSiblingProfilePaths()) {
            if (File(sibling).exists()) paths.add(sibling)
        }

        for (path in paths) {
            writeUnsetBlockToFile(path)
        }
    }

    private fun writeUnsetBlockToFile(path: String) {
        val powershell = isPowerShell(path)
        val block = buildUnsetBlock(powershell)

        writeAtomic(path, block, powershell).also { ok ->
            if (ok) logger.info("Unset block written to $path")
            else logger.warn("Failed to write unset block to $path")
        }
    }

    private fun buildUnsetBlock(powershell: Boolean): String = buildString {
        if (powershell) {
            appendLine("<# claude-api-switch #>")
            deepseekEnvVars.forEach { appendLine("Remove-Item Env:$it -ErrorAction SilentlyContinue") }
            appendLine("<# claude-api-switch-end #>")
        } else {
            appendLine("# claude-api-switch")
            deepseekEnvVars.forEach { appendLine("unset $it 2>/dev/null || true") }
            appendLine("# claude-api-switch-end")
        }
    }

    // ---- Atomic file write ----

    private fun writeAtomic(path: String, block: String, powershell: Boolean): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()

            val existing = if (file.exists()) file.readText() else ""
            val cleaned = removeMarkerBlock(existing, powershell)
            val updated = when {
                cleaned.isBlank() -> "$block\n"
                !cleaned.endsWith("\n") -> "$cleaned\n\n$block\n"
                else -> "$cleaned\n$block\n"
            }

            val tmp = File("$path.tmp")
            tmp.writeText(updated)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- Current mode detection ----

    /** Returns true if the plugin's export block (DeepSeek active) is present in the profile. */
    fun hasExportBlock(): Boolean {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return false
        return try {
            val file = File(profilePath)
            if (!file.exists()) return false
            val content = file.readText()
            if (isPowerShell(profilePath)) {
                Regex("""<# claude-api-switch #>[\s\S]*?\${'$'}env:ANTHROPIC_BASE_URL[\s\S]*?<# claude-api-switch-end #>""")
                    .containsMatchIn(content)
            } else {
                Regex("""# claude-api-switch\r?\n[\s\S]*?export ANTHROPIC_BASE_URL=[\s\S]*?# claude-api-switch-end""")
                    .containsMatchIn(content)
            }
        } catch (_: Exception) {
            false
        }
    }

    // ---- Foreign env var detection ----

    fun findForeignClaudeEnvVars(): List<String> {
        val profilePath = resolveProfilePath()
        if (profilePath.isBlank()) return emptyList()

        try {
            val file = File(profilePath)
            if (!file.exists()) return emptyList()

            val content = file.readText()
            val powershell = isPowerShell(profilePath)
            val cleaned = removeMarkerBlock(content, powershell)

            val regex = if (powershell) {
                Regex("""^\s*[$]env:(ANTHROPIC_\w+|CLAUDE_CODE_\w+)\s*=""", RegexOption.MULTILINE)
            } else {
                Regex("""^\s*export\s+(ANTHROPIC_\w+|CLAUDE_CODE_\w+)=""", RegexOption.MULTILINE)
            }

            return regex.findAll(cleaned).map { it.groupValues[1] }.toList()
        } catch (e: Exception) {
            logger.warn("Failed to scan profile for foreign env vars: ${e.message}")
            return emptyList()
        }
    }

    // ---- Text-level processing (internal visibility for tests) ----

    internal fun stripAllClaudeEnvVars(content: String, isPowerShell: Boolean): String {
        var cleaned = removeMarkerBlock(content, isPowerShell)

        val foreignRegex = if (isPowerShell) {
            Regex("""^[ \t]*[$]env:(ANTHROPIC_\w+|CLAUDE_CODE_\w+)\s*=\s*.*(\r?\n|${'$'})""", RegexOption.MULTILINE)
        } else {
            Regex("""^[ \t]*export\s+(ANTHROPIC_\w+|CLAUDE_CODE_\w+)=.*(\r?\n|${'$'})""", RegexOption.MULTILINE)
        }
        cleaned = foreignRegex.replace(cleaned, "")
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
}
