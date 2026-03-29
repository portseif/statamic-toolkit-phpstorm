import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
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
        create(
            providers.gradleProperty("platformType").get(),
            providers.gradleProperty("platformVersion").get()
        )
        bundledPlugin("com.intellij.css")
        bundledPlugin("JavaScript")

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuildVersion")
            untilBuild = providers.gradleProperty("untilBuildVersion")
        }
    }
}

// Grammar-Kit: generate lexer from .flex file
val generateAntlersLexer by tasks.registering(org.jetbrains.grammarkit.tasks.GenerateLexerTask::class) {
    sourceFile.set(file("grammars/Antlers.flex"))
    targetOutputDir.set(file("src/main/java/com/antlers/support/lexer"))
}

tasks {
    compileJava {
        dependsOn(generateAntlersLexer)
    }

    compileKotlin {
        dependsOn(generateAntlersLexer)
    }
}

sourceSets {
    main {
        java.srcDirs("src/main/java", "src/main/kotlin")
    }
}
