import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.io.File

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

fun extractChangeNotes(changelog: File): String {
    if (!changelog.exists()) return "<p>Initial release.</p>"

    val lines = changelog.readLines()
    val html = StringBuilder()
    var insideList = false

    fun closeListIfNeeded() {
        if (insideList) {
            html.append("</ul>")
            insideList = false
        }
    }

    fun convertInlineMarkdown(text: String): String {
        return text.replace(Regex("`(.+?)`"), "<code>$1</code>")
    }

    lines.forEach { line ->
        when {
            line.startsWith("## [") -> {
                closeListIfNeeded()
                val version = line.substringAfter("[").substringBefore("]")
                html.append("<h2>$version</h2>")
            }
            line.startsWith("- ") -> {
                if (!insideList) {
                    html.append("<ul>")
                    insideList = true
                }
                html.append("<li>")
                html.append(convertInlineMarkdown(line.removePrefix("- ").trim()))
                html.append("</li>")
            }
            line.isBlank() -> closeListIfNeeded()
            line.startsWith("# ") -> {} // skip top-level heading
            else -> {
                closeListIfNeeded()
                html.append("<p>")
                html.append(convertInlineMarkdown(line.trim()))
                html.append("</p>")
            }
        }
    }

    closeListIfNeeded()
    return html.toString()
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType").get(),
            providers.gradleProperty("platformVersion").get()
        )
        bundledPlugin("com.intellij.css")
        bundledPlugin("JavaScript")
        bundledPlugin("com.jetbrains.php")

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        changeNotes = providers.provider {
            extractChangeNotes(layout.projectDirectory.file("CHANGELOG.md").asFile)
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuildVersion")
            untilBuild = providers.gradleProperty("untilBuildVersion")
        }
    }
}

// Grammar-Kit: generate lexer from .flex file
val generateAntlersLexer by tasks.registering(org.jetbrains.grammarkit.tasks.GenerateLexerTask::class) {
    sourceFile.set(file("grammars/Antlers.flex"))
    targetOutputDir.set(file("src/main/gen/com/antlers/support/lexer"))
}

// Grammar-Kit: generate parser from .bnf file
val generateAntlersParser by tasks.registering(org.jetbrains.grammarkit.tasks.GenerateParserTask::class) {
    sourceFile.set(file("grammars/Antlers.bnf"))
    targetRootOutputDir.set(file("src/main/gen"))
    pathToParser.set("/com/antlers/support/parser/AntlersParser.java")
    pathToPsiRoot.set("/com/antlers/support/psi")
}

tasks {
    compileJava {
        dependsOn(generateAntlersLexer, generateAntlersParser)
    }

    compileKotlin {
        dependsOn(generateAntlersLexer, generateAntlersParser)
    }
}

sourceSets {
    main {
        java.srcDirs("src/main/java", "src/main/kotlin", "src/main/gen")
    }
}
