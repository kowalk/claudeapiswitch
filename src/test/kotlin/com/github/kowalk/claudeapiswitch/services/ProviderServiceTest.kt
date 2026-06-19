package com.github.kowalk.claudeapiswitch.services

import org.junit.Assert.*
import org.junit.Test

class ProviderServiceTest {

    private val service = ProviderService()

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

        val result = service.removeMarkerBlock(content, false)

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

        val result = service.removeMarkerBlock(content, false)

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

        val result = service.removeMarkerBlock(content, false)

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

        val result = service.removeMarkerBlock(content, false)

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

        val result = service.removeMarkerBlock(content, true)

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

        val result = service.removeMarkerBlock(content, false)

        assertEquals(content, result)
    }

    @Test
    fun testRemoveBashMarkersCrlf() {
        // Bash profile with Windows CRLF line endings — must still strip the block
        val content = "# some config\r\n# claude-api-switch\r\nexport ANTHROPIC_BASE_URL=\"x\"\r\n# claude-api-switch-end\r\n# other config\r\n"

        val result = service.removeMarkerBlock(content, false)

        assertFalse(result.contains("claude-api-switch"))
        assertFalse(result.contains("ANTHROPIC_BASE_URL"))
        assertTrue(result.contains("some config"))
        assertTrue(result.contains("other config"))
    }

    @Test
    fun testRemoveOldBashMarkersCrlf() {
        val content = "# claude-deepseek-switch\r\nexport ANTHROPIC_BASE_URL=\"x\"\r\n# claude-deepseek-switch-end\r\n"

        val result = service.removeMarkerBlock(content, false)

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

        val result = service.removeMarkerBlock(content, false)

        assertFalse(result.contains("ANTHROPIC"))
        assertFalse(result.contains("CLAUDE_CODE"))
        assertFalse(result.contains("claude-api-switch"))
    }
}
