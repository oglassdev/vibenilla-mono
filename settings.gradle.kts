pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()

        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            content {
                includeModule("net.minestom", "minestom")
                includeModule("net.minestom", "testing")
            }
        }
    }
}

rootProject.name = "vibenilla-mono"

include("pvp", "worldgen", "placement", "crafting", "fluids", "loot", "containers", "server")
