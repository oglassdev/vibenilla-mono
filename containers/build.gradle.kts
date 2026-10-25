plugins {
    id("vibenilla.library")
}

description = "Chests, barrels and furnaces for Minestom"
vibenilla.license = License.MIT

dependencies {
    compileOnly(libs.slf4j.api)
    implementation(project(":loot"))
    implementation(project(":crafting"))

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
