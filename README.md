# Claude API Switch — PhpStorm Plugin

Toggle [Claude Code](https://claude.com/claude-code) between Anthropic's native API and [DeepSeek's Anthropic-compatible API](https://api-docs.deepseek.com/quick_start/agent_integrations/claude_code) directly from the IDE.

## Features

- **Toolbar icon** — one-click toggle from the top toolbar, right next to the Claude Code icon
- **Status bar indicator** — shows current provider (🟠 Anthropic / 🔵 DeepSeek) with one-click switch
- **Tools menu** — `Tools → Claude API Switch` with labeled toggle actions and icons
- **Profile synchronization** — writes all required environment variables to your shell profile so `claude` works in any terminal, not just inside PhpStorm
- **Foreign var detection** — warns if manually configured Claude Code env vars outside the plugin's control are found in your profile
- **Settings page** — `Settings → Tools → Claude API Switch` for API key (stored in OS keychain), model selection, and profile path

## Environment Variables Managed

All variables follow the [DeepSeek Claude Code integration guide](https://api-docs.deepseek.com/quick_start/agent_integrations/claude_code):

| Variable | Default |
|---|---|
| `ANTHROPIC_BASE_URL` | `https://api.deepseek.com/anthropic` |
| `ANTHROPIC_AUTH_TOKEN` | Your DeepSeek API key |
| `ANTHROPIC_MODEL` | `deepseek-v4-pro[1m]` |
| `ANTHROPIC_DEFAULT_OPUS_MODEL` | `deepseek-v4-pro[1m]` |
| `ANTHROPIC_DEFAULT_SONNET_MODEL` | `deepseek-v4-pro[1m]` |
| `ANTHROPIC_DEFAULT_HAIKU_MODEL` | `deepseek-v4-flash` |
| `CLAUDE_CODE_SUBAGENT_MODEL` | `deepseek-v4-flash` |
| `CLAUDE_CODE_EFFORT_LEVEL` | `max` |

## Installation

### From ZIP (recommended)

1. Download `claude-api-switch-*.zip` from [Releases](https://github.com/kowalk/claude-api-switch/releases)
2. In PhpStorm: **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Select the ZIP file and restart PhpStorm

### From Source

```bash
./gradlew buildPlugin
# Plugin ZIP is at: build/distributions/claude-api-switch-*.zip
```

## Usage

### Initial Setup

1. Get a [DeepSeek API key](https://platform.deepseek.com/)
2. Go to **Settings → Tools → Claude API Switch**
3. Paste your API key (stored securely in your OS keychain)
4. Keep "Sync environment variables to shell profile file" enabled

### Switching Providers

- **Toolbar**: click the icon in the top toolbar
- **Status bar**: click the indicator at the bottom right
- **Menu**: `Tools → Claude API Switch → Switch to Anthropic/DeepSeek API`

After switching, restart any active Claude Code sessions for the change to take effect.

## Requirements

- PhpStorm 2026.1 or later
- [Claude Code](https://claude.com/claude-code) installed (`npm install -g @anthropic-ai/claude-code`)
- A [DeepSeek API key](https://platform.deepseek.com/) (only needed for DeepSeek mode)

## License

MIT
