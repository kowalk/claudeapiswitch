package com.github.kowalk.claudeapiswitch.services

import com.intellij.openapi.diagnostic.Logger
import org.junit.Assert.*
import org.junit.Test

class ProviderServiceTest {

    private val profileManager = ShellProfileManager(
        Logger.getInstance(ShellProfileManager::class.java),
        listOf(
            "ANTHROPIC_BASE_URL", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_MODEL",
            "ANTHROPIC_DEFAULT_OPUS_MODEL", "ANTHROPIC_DEFAULT_SONNET_MODEL",
            "ANTHROPIC_DEFAULT_HAIKU_MODEL", "CLAUDE_CODE_SUBAGENT_MODEL",
            "CLAUDE_CODE_EFFORT_LEVEL"
        )
    )

    @Test
    fun testRemoveNewBashMarkers() {
        val content = """
            # some config
            # claude-api-switch
            export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
            export ANTHROPIC_MODEL="deepseek-v4-pro"
            # claude-api-switch-end
            # other config
        """.trimIndent()

        val result = profileManager.removeMarkerBlock(content, false)

        assertFalse(result.contains("claude-api-switch"))
        assertTrue(result.contains("other config"))
        assertTrue(result.contains("some config"))
        assertFalse(result.contains("ANTHROPIC_BASE_URL"))
    }

    @Test
    fun testRemoveOldBashMarkers() {
        val content = """
            # claude-deepseek-switch
            export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
            # claude-deepseek-switch-end
        """.trimIndent()

        val result = profileManager.removeMarkerBlock(content, false)

        assertFalse(result.contains("claude-deepseek-switch"))
        assertFalse(result.contains("ANTHROPIC_BASE_URL"))
    }

    @Test
    fun testRemoveMultipleBlocks() {
        val content = """
            # claude-api-switch
            export ANTHROPIC_MODEL="model1"
            # claude-api-switch-end
            # middle content
            # claude-deepseek-switch
            export ANTHROPIC_MODEL="model2"
            # claude-deepseek-switch-end
        """.trimIndent()

        val result = profileManager.removeMarkerBlock(content, false)

        assertFalse(result.contains("claude-api-switch"))
        assertFalse(result.contains("claude-deepseek-switch"))
        assertFalse(result.contains("model1"))
        assertFalse(result.contains("model2"))
        assertTrue(result.contains("middle content"))
    }

    @Test
    fun testPreservesContentOutsideMarkers() {
        val content = """
            export PATH=/usr/bin
            export JAVA_HOME=/opt/java

            # claude-api-switch
            export ANTHROPIC_BASE_URL="x"
            # claude-api-switch-end

            alias ll='ls -la'
        """.trimIndent()

        val result = profileManager.removeMarkerBlock(content, false)

        assertTrue(result.contains("export PATH=/usr/bin"))
        assertTrue(result.contains("export JAVA_HOME=/opt/java"))
        assertTrue(result.contains("alias ll='ls -la'"))
        assertFalse(result.contains("ANTHROPIC_BASE_URL"))
    }

    @Test
    fun testRemovePowerShellMarkers() {
        val content = """
            # some config
            <# claude-api-switch #>
            ${'$'}env:ANTHROPIC_BASE_URL = "https://api.deepseek.com/anthropic"
            <# claude-api-switch-end #>
            # other config
        """.trimIndent()

        val result = profileManager.removeMarkerBlock(content, true)

        assertFalse(result.contains("claude-api-switch"))
        assertTrue(result.contains("other config"))
        assertFalse(result.contains("ANTHROPIC_BASE_URL"))
    }

    @Test
    fun testNoMarkersReturnsUnchanged() {
        val content = """
            export PATH=/usr/bin
            export HOME=/home/user
        """.trimIndent()

        val result = profileManager.removeMarkerBlock(content, false)

        assertEquals(content, result)
    }

    @Test
    fun testRemoveBashMarkersCrlf() {
        val content = "# some config\r\n# claude-api-switch\r\nexport ANTHROPIC_BASE_URL=\"x\"\r\n# claude-api-switch-end\r\n# other config\r\n"

        val result = profileManager.removeMarkerBlock(content, false)

        assertFalse(result.contains("claude-api-switch"))
        assertFalse(result.contains("ANTHROPIC_BASE_URL"))
        assertTrue(result.contains("some config"))
        assertTrue(result.contains("other config"))
    }

    @Test
    fun testRemoveOldBashMarkersCrlf() {
        val content = "# claude-deepseek-switch\r\nexport ANTHROPIC_BASE_URL=\"x\"\r\n# claude-deepseek-switch-end\r\n"

        val result = profileManager.removeMarkerBlock(content, false)

        assertFalse(result.contains("claude-deepseek-switch"))
        assertFalse(result.contains("ANTHROPIC_BASE_URL"))
    }

    @Test
    fun testRemovesAllEightEnvVars() {
        val content = """
            # claude-api-switch
            export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
            export ANTHROPIC_AUTH_TOKEN="sk-key"
            export ANTHROPIC_MODEL="deepseek-v4-pro[1m]"
            export ANTHROPIC_DEFAULT_OPUS_MODEL="deepseek-v4-pro[1m]"
            export ANTHROPIC_DEFAULT_SONNET_MODEL="deepseek-v4-pro[1m]"
            export ANTHROPIC_DEFAULT_HAIKU_MODEL="deepseek-v4-flash"
            export CLAUDE_CODE_SUBAGENT_MODEL="deepseek-v4-flash"
            export CLAUDE_CODE_EFFORT_LEVEL="max"
            # claude-api-switch-end
        """.trimIndent()

        val result = profileManager.removeMarkerBlock(content, false)

        assertFalse(result.contains("ANTHROPIC"))
        assertFalse(result.contains("CLAUDE_CODE"))
        assertFalse(result.contains("claude-api-switch"))
    }

    // --- stripAllClaudeEnvVars tests ---

    @Test
    fun testStripAllRemovesForeignEnvVarsOutsideMarkers() {
        val content = """
            export PATH=/usr/bin
            export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
            export ANTHROPIC_AUTH_TOKEN="sk-manual-key"
            export JAVA_HOME=/opt/java
            alias ll='ls -la'
        """.trimIndent()

        val result = profileManager.stripAllClaudeEnvVars(content, false)

        assertTrue(result.contains("export PATH=/usr/bin"))
        assertTrue(result.contains("export JAVA_HOME=/opt/java"))
        assertTrue(result.contains("alias ll='ls -la'"))
        assertFalse("foreign ANTHROPIC_BASE_URL should be stripped",
            result.contains("ANTHROPIC_BASE_URL"))
        assertFalse("foreign ANTHROPIC_AUTH_TOKEN should be stripped",
            result.contains("ANTHROPIC_AUTH_TOKEN"))
    }

    @Test
    fun testStripAllRemovesBothMarkersAndForeignVars() {
        val content = """
            # claude-api-switch
            export ANTHROPIC_MODEL="model1"
            # claude-api-switch-end
            export PATH=/usr/bin
            export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
            export CLAUDE_CODE_EFFORT_LEVEL="high"
        """.trimIndent()

        val result = profileManager.stripAllClaudeEnvVars(content, false)

        assertTrue(result.contains("export PATH=/usr/bin"))
        assertFalse("marker block ANTHROPIC_MODEL should be stripped",
            result.contains("ANTHROPIC_MODEL"))
        assertFalse("foreign ANTHROPIC_BASE_URL should be stripped",
            result.contains("ANTHROPIC_BASE_URL"))
        assertFalse("foreign CLAUDE_CODE_EFFORT_LEVEL should be stripped",
            result.contains("CLAUDE_CODE_EFFORT_LEVEL"))
        assertFalse(result.contains("claude-api-switch"))
    }

    @Test
    fun testStripAllLeavesNonClaudeVarsUntouched() {
        val content = """
            export PATH=/usr/bin
            export JAVA_HOME=/opt/java
            export NODE_ENV=development
            alias ll='ls -la'
        """.trimIndent()

        val result = profileManager.stripAllClaudeEnvVars(content, false)

        assertEquals(content.trim(), result.trim())
    }

    @Test
    fun testStripAllCleansPowerShellForeignVars() {
        val content = """
            ${'$'}env:PATH = "C:\\bin"
            ${'$'}env:ANTHROPIC_BASE_URL = "https://api.deepseek.com/anthropic"
            ${'$'}env:CLAUDE_CODE_EFFORT_LEVEL = "max"
            # other config
        """.trimIndent()

        val result = profileManager.stripAllClaudeEnvVars(content, true)

        assertTrue(result.contains("${'$'}env:PATH"))
        assertTrue(result.contains("other config"))
        assertFalse(result.contains("ANTHROPIC_BASE_URL"))
        assertFalse(result.contains("CLAUDE_CODE_EFFORT_LEVEL"))
    }

    @Test
    fun testStripAllCollapsesBlankLines() {
        val content = """
            export PATH=/usr/bin


            export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"


            export JAVA_HOME=/opt/java
        """.trimIndent()

        val result = profileManager.stripAllClaudeEnvVars(content, false)

        assertFalse(result.contains("ANTHROPIC"))
        assertFalse(Regex("""\n{3,}""").containsMatchIn(result))
        assertTrue(result.contains("export PATH"))
        assertTrue(result.contains("export JAVA_HOME"))
    }

    // --- unset block (IDE process-env neutralisation) ---

    @Test
    fun testRemoveMarkerBlockStripsUnsetBlock() {
        val content = """
            export PATH=/usr/bin
            # claude-api-switch
            unset ANTHROPIC_BASE_URL 2>/dev/null || true
            unset ANTHROPIC_AUTH_TOKEN 2>/dev/null || true
            unset ANTHROPIC_MODEL 2>/dev/null || true
            unset ANTHROPIC_DEFAULT_OPUS_MODEL 2>/dev/null || true
            unset ANTHROPIC_DEFAULT_SONNET_MODEL 2>/dev/null || true
            unset ANTHROPIC_DEFAULT_HAIKU_MODEL 2>/dev/null || true
            unset CLAUDE_CODE_SUBAGENT_MODEL 2>/dev/null || true
            unset CLAUDE_CODE_EFFORT_LEVEL 2>/dev/null || true
            # claude-api-switch-end
            export JAVA_HOME=/opt/java
        """.trimIndent()

        val result = profileManager.removeMarkerBlock(content, false)

        assertTrue(result.contains("export PATH=/usr/bin"))
        assertTrue(result.contains("export JAVA_HOME=/opt/java"))
        assertFalse("unset block markers must be removed",
            result.contains("claude-api-switch"))
        assertFalse("unset commands must be removed",
            result.contains("unset ANTHROPIC"))
        assertFalse(result.contains("unset CLAUDE_CODE"))
    }

    @Test
    fun testStripAllDoesNotStripUnsetCommandsOutsideBlock() {
        val content = """
            export PATH=/usr/bin
            unset ANTHROPIC_BASE_URL 2>/dev/null || true
            export JAVA_HOME=/opt/java
        """.trimIndent()

        val result = profileManager.stripAllClaudeEnvVars(content, false)

        assertTrue(result.contains("unset ANTHROPIC_BASE_URL"))
        assertTrue(result.contains("export PATH"))
        assertTrue(result.contains("export JAVA_HOME"))
    }
}
