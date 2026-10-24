plugins {
    id("vibenilla.library")
}

description = "Chests and barrels for Minestom"
vibenilla.license = License.MIT

dependencies {
    compileOnly(libs.slf4j.api)
    implementation(project(":loot"))

    testImplementation(libs.minestom.testing)
}
