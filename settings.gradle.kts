pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.github.com/axelsteffen/apk2maven-gradle-plugin")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
            }
        }
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("com.android.library") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.1.21"
        id("de.developer-leipzig.gradle.apk2maven") version "0.1.1"
    }
}

plugins {
    // Auto-provision JDKs for Daemon JVM criteria (Java 25 hosts must run the daemon on JDK 21)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "SmartTublex"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

include(":apk-base")
include(":app")

// PlexServiceCore submodule — build files live under gradle/ (not the legacy AGP 3 scripts).
include(":plexserviceinterfaces")
project(":plexserviceinterfaces").projectDir = file("PlexServiceCore/plexserviceinterfaces")
project(":plexserviceinterfaces").buildFileName = "../../gradle/plexserviceinterfaces.gradle.kts"

include(":plexapi")
project(":plexapi").projectDir = file("PlexServiceCore/plexapi")
project(":plexapi").buildFileName = "../../gradle/plexapi.gradle.kts"
