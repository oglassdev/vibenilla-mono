plugins {
    id("vibenilla.library")
}

description = "Loot table parser and evaluator for Minestom"
vibenilla.license = License.MIT

dependencies {
    compileOnly(libs.fastutil)

    testImplementation(libs.minestom.testing)
}
