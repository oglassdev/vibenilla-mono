plugins {
    id("vibenilla.library")
}

description = "Loot table parser and evaluator for Minestom"
vibenilla.license = License.MIT

dependencies {
    compileOnly(libs.fastutil)

    testImplementation(libs.minestom.testing)
}

val vanillaData =
    tasks.register<VanillaDataTask>("vanillaData") {
        minecraftVersion = libs.versions.minecraft
        outputDirectory = layout.buildDirectory.dir("vanilla")
    }

tasks.test {
    dependsOn(vanillaData)
    workingDir = vanillaData.flatMap { it.outputDirectory }.get().asFile
}
