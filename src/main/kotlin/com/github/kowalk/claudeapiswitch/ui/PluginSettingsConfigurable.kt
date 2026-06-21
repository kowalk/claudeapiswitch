package com.github.kowalk.claudeapiswitch.ui

import com.github.kowalk.claudeapiswitch.settings.PluginSettings
import com.github.kowalk.claudeapiswitch.services.ProviderService
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

class PluginSettingsConfigurable : Configurable {

    private val service get() = ProviderService.getInstance()
    private val settings get() = PluginSettings.getInstance().appState

    private var apiKeyField = JBPasswordField()
    private var baseUrlField = JBTextField()
    private var modelField = JBTextField()
    private var opusModelField = JBTextField()
    private var sonnetModelField = JBTextField()
    private var haikuModelField = JBTextField()
    private var subagentModelField = JBTextField()
    private var effortLevelField = JBTextField()
    private var syncToProfileCheckbox = JBCheckBox("Sync environment variables to shell profile file")
    private var profilePathField = JBTextField()

    private var panel: JPanel? = null
    private var resetApiKey: String = ""

    override fun getDisplayName(): String = "Claude API Switch"

    override fun createComponent(): JComponent {
        apiKeyField = JBPasswordField()
        baseUrlField = JBTextField()
        modelField = JBTextField()
        opusModelField = JBTextField()
        sonnetModelField = JBTextField()
        haikuModelField = JBTextField()
        subagentModelField = JBTextField()
        effortLevelField = JBTextField()
        syncToProfileCheckbox = JBCheckBox("Sync environment variables to shell profile file")
        profilePathField = JBTextField()

        panel = JPanel(GridBagLayout())
        val p = panel!!
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(4, 5, 4, 5)
        c.gridwidth = 1
        c.weightx = 0.0

        var row = 0

        // --- DeepSeek API Configuration ---
        addSection(p, c, row++, "DeepSeek API Configuration")

        // API Key
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("API Key:"), c)
        c.gridx = 1; c.weightx = 1.0
        apiKeyField.columns = 35
        apiKeyField.toolTipText = "Your DeepSeek API key. Stored securely in the OS keychain."
        p.add(apiKeyField, c)
        row++

        // Base URL
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("Base URL:"), c)
        c.gridx = 1; c.weightx = 1.0
        baseUrlField.columns = 35
        baseUrlField.toolTipText = "ANTHROPIC_BASE_URL value"
        p.add(baseUrlField, c)
        row++

        // Primary Model
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("Primary model:"), c)
        c.gridx = 1; c.weightx = 1.0
        modelField.columns = 35
        modelField.toolTipText = "ANTHROPIC_MODEL — default model for all tasks"
        p.add(modelField, c)
        row++

        // --- Model Mapping ---
        addSection(p, c, row++, "Model Mapping")

        // Opus Model
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("Default Opus model:"), c)
        c.gridx = 1; c.weightx = 1.0
        opusModelField.columns = 35
        opusModelField.toolTipText = "ANTHROPIC_DEFAULT_OPUS_MODEL — used when Opus tier is requested"
        p.add(opusModelField, c)
        row++

        // Sonnet Model
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("Default Sonnet model:"), c)
        c.gridx = 1; c.weightx = 1.0
        sonnetModelField.columns = 35
        sonnetModelField.toolTipText = "ANTHROPIC_DEFAULT_SONNET_MODEL — used when Sonnet tier is requested"
        p.add(sonnetModelField, c)
        row++

        // Haiku Model
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("Default Haiku model:"), c)
        c.gridx = 1; c.weightx = 1.0
        haikuModelField.columns = 35
        haikuModelField.toolTipText = "ANTHROPIC_DEFAULT_HAIKU_MODEL — used when Haiku tier is requested"
        p.add(haikuModelField, c)
        row++

        // Subagent Model
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("Subagent model:"), c)
        c.gridx = 1; c.weightx = 1.0
        subagentModelField.columns = 35
        subagentModelField.toolTipText = "CLAUDE_CODE_SUBAGENT_MODEL — model for spawned subagents"
        p.add(subagentModelField, c)
        row++

        // Effort Level
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("Effort level:"), c)
        c.gridx = 1; c.weightx = 1.0
        effortLevelField.columns = 35
        effortLevelField.toolTipText = "CLAUDE_CODE_EFFORT_LEVEL — reasoning effort: low, medium, high, max"
        p.add(effortLevelField, c)
        row++

        // --- Profile Sync ---
        addSection(p, c, row++, "Shell Profile Synchronization")

        // Sync checkbox
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JPanel(), c)
        c.gridx = 1; c.weightx = 1.0
        syncToProfileCheckbox.toolTipText = "When enabled, all ANTHROPIC_* env vars are written to your shell " +
                "profile so Claude Code works in any terminal as well."
        p.add(syncToProfileCheckbox, c)
        row++

        // Profile path
        c.gridy = row; c.gridx = 0; c.weightx = 0.0
        p.add(JBLabel("Profile path:"), c)
        c.gridx = 1; c.weightx = 1.0
        profilePathField.columns = 50
        profilePathField.toolTipText = """Path to your shell profile file. Auto-detected based on OS.
WSL users: set this to your WSL ~/.bashrc path, e.g. \\wsl${'$'}\Ubuntu\home\user\.bashrc"""
        p.add(profilePathField, c)
        row++

        // Filler
        c.gridy = row; c.gridx = 0; c.weightx = 1.0; c.weighty = 1.0; c.gridwidth = 2
        c.fill = GridBagConstraints.BOTH
        p.add(JPanel(), c)

        reset()
        return p
    }

    override fun isModified(): Boolean {
        val key = String(apiKeyField.password)
        return modelField.text != settings.deepseekModel ||
                baseUrlField.text != settings.deepseekBaseUrl ||
                opusModelField.text != settings.deepseekOpusModel ||
                sonnetModelField.text != settings.deepseekSonnetModel ||
                haikuModelField.text != settings.deepseekHaikuModel ||
                subagentModelField.text != settings.deepseekSubagentModel ||
                effortLevelField.text != settings.deepseekEffortLevel ||
                syncToProfileCheckbox.isSelected != settings.syncToProfile ||
                profilePathField.text != settings.profilePath ||
                key != resetApiKey
    }

    override fun apply() {
        val key = String(apiKeyField.password)

        settings.deepseekModel = modelField.text
        settings.deepseekBaseUrl = baseUrlField.text
        settings.deepseekOpusModel = opusModelField.text
        settings.deepseekSonnetModel = sonnetModelField.text
        settings.deepseekHaikuModel = haikuModelField.text
        settings.deepseekSubagentModel = subagentModelField.text
        settings.deepseekEffortLevel = effortLevelField.text
        settings.syncToProfile = syncToProfileCheckbox.isSelected
        settings.profilePath = profilePathField.text

        service.setDeepSeekApiKey(key.ifBlank { null })
    }

    override fun reset() {
        resetApiKey = service.getDeepSeekApiKey() ?: ""
        apiKeyField.text = resetApiKey
        baseUrlField.text = settings.deepseekBaseUrl
        modelField.text = settings.deepseekModel
        opusModelField.text = settings.deepseekOpusModel
        sonnetModelField.text = settings.deepseekSonnetModel
        haikuModelField.text = settings.deepseekHaikuModel
        subagentModelField.text = settings.deepseekSubagentModel
        effortLevelField.text = settings.deepseekEffortLevel
        syncToProfileCheckbox.isSelected = settings.syncToProfile
        profilePathField.text = settings.profilePath.ifBlank { service.getDefaultProfilePath() }
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun addSection(panel: JPanel, c: GridBagConstraints, row: Int, title: String) {
        c.gridy = row
        c.gridx = 0; c.weightx = 0.0
        panel.add(JBLabel("<html><b>$title</b></html>"), c)
        c.gridx = 1; c.weightx = 1.0
        panel.add(JPanel(), c)
    }
}
