plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against IntelliJ IDEA as the reference platform for cross-IDE compatibility
        intellijIdea(providers.gradleProperty("platformVersion").get())
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "261.*"
        }
        changeNotes = """
            <h3>1.0.11</h3>
            <ul>
                <li>Cross-IDE support: now works in all JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, PhpStorm, etc.)</li>
                <li>Widened compatibility range: 2025.1 — 2026.1.x</li>
            </ul>
            <h3>1.0.0</h3>
            <ul>
                <li>Initial release: toggle Claude Code between Anthropic and DeepSeek APIs</li>
                <li>Status bar widget with one-click provider switching</li>
                <li>Shell profile file synchronization for CLI usage</li>
                <li>Cross-platform: Windows, macOS, Linux</li>
            </ul>
        """.trimIndent()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    jvmArgs("-Xlog:cds=off")
}
