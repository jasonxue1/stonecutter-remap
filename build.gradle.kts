import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    signing
    id("com.diffplug.spotless") version "8.5.1"
    id("com.vanniktech.maven.publish") version "0.36.0"
}

val pluginVersion: String by project
val stonecutterVersion: String by project
val remapVersion: String by project

group = "dev.jasonxue"

val githubRefType = System.getenv("GITHUB_REF_TYPE").orEmpty()
val githubRefName = System.getenv("GITHUB_REF_NAME").orEmpty()
val isTagRelease = githubRefType == "tag"
val expectedTagName = "v$pluginVersion"
val isPublishRequested = gradle.startParameter.taskNames.any { taskName -> taskName.startsWith("publish") }
val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent

if (isTagRelease && githubRefName != expectedTagName) {
    error("Release tag '$githubRefName' does not match pluginVersion '$pluginVersion'. Expected '$expectedTagName'.")
}

val publishVersion =
    when {
        isTagRelease -> pluginVersion
        else -> "$pluginVersion-SNAPSHOT"
    }

version = publishVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.kikugie.dev/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("dev.kikugie:stonecutter:$stonecutterVersion")
    implementation("com.github.Fallen-Breath:remap:$remapVersion")
}

gradlePlugin {
    plugins {
        create("stonecutterRemap") {
            id = "dev.jasonxue.stonecutter.remap"
            implementationClass = "dev.jasonxue.stonecutter.remap.StonecutterRemapRootPlugin"
        }
        create("stonecutterRemapWorker") {
            id = "dev.jasonxue.stonecutter.remap.worker"
            implementationClass = "dev.jasonxue.stonecutter.remap.StonecutterRemapWorkerPlugin"
        }
    }
}

extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
    publishToMavenCentral(automaticRelease = true)
    if (isPublishRequested) {
        check(hasSigningKey) {
            "Publishing requires Gradle property 'signingInMemoryKey' " +
                "(for GitHub Actions, set ORG_GRADLE_PROJECT_signingInMemoryKey)."
        }
        signAllPublications()
    }
    coordinates(
        groupId = project.group.toString(),
        artifactId = project.name,
        version = project.version.toString(),
    )

    pom {
        name.set("Stonecutter Remap")
        description.set("Bridge plugin that integrates Stonecutter multi-version builds with ReplayMod remap.")
        url.set("https://github.com/jasonxue1/stonecutter-remap")
        licenses {
            license {
                name.set("GNU General Public License v3.0 or later")
                url.set("https://www.gnu.org/licenses/gpl-3.0-standalone.html")
            }
        }
        developers {
            developer {
                id.set("jasonxue1")
                name.set("jasonxue")
                email.set("hi@jasonxue.dev")
                url.set("https://github.com/jasonxue1")
            }
        }
        scm {
            url.set("https://github.com/jasonxue1/stonecutter-remap")
            connection.set("scm:git:https://github.com/jasonxue1/stonecutter-remap.git")
            developerConnection.set("scm:git:git@github.com:jasonxue1/stonecutter-remap.git")
        }
    }
}

spotless {
    val licenseHeaderFile = rootProject.file("copyright.txt")
    kotlin {
        target("src/**/*.kt")
        ktlint()
        licenseHeaderFile(licenseHeaderFile, "^package")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("yaml") {
        target(".github/**/*.yml", ".github/**/*.yaml")
        prettier(
            mapOf(
                "prettier" to "3.6.2",
            ),
        )
    }
    format("text") {
        target("*.properties", "gradle/wrapper/gradle-wrapper.properties", "LICENSE")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named<Jar>("jar") {
    inputs.property("artifact_name", project.name)
    from(rootProject.file("LICENSE")) {
        rename { name -> "${name}_${inputs.properties["artifact_name"]}" }
    }
}
