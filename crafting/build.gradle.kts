plugins {
    id("vibenilla.library")
}

description = "Crafting for Minestom"
vibenilla.license = License.APACHE_2

dependencies {
    compileOnly(libs.fastutil)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.fastutil)
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
