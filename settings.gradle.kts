plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "vibenilla-mono"

include("pvp", "worldgen", "placement", "crafting", "fluids", "loot")
