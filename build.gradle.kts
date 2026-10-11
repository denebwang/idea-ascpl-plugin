plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "org.acspl.intellij"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIdePath = providers.gradleProperty("localIdePath")
        if (localIdePath.isPresent) local(localIdePath.get())
        else intellijIdea(providers.gradleProperty("platformVersion").get())
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        pluginVerifier("1.410")
    }
    testImplementation("junit:junit:4.13.2")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

intellijPlatform {
    pluginConfiguration {
        id = "acspl.language"
        name = "ACSPL+"
        version = project.version.toString()
        ideaVersion { sinceBuild = "262"; untilBuild = "262.*" }
    }
    pluginVerification {
        ides {
            // Explicitly check the minimum supported platform.
            create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea, "2026.2")
            providers.gradleProperty("verifyIdePaths").orNull
                ?.split("|", ",")?.filter { it.isNotBlank() }?.forEach { local(it) }
        }
    }
}

tasks.test {
    maxHeapSize = "2g"
    systemProperty("java.awt.headless", "true")
    providers.gradleProperty("mockSdkDll").orNull?.let { systemProperty("acspl.mock.sdk", it) }
    testLogging { events("failed", "skipped"); showStandardStreams = false }
}
