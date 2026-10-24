plugins {
    id("vibenilla.library")
}

description = "Water and lava physics for Minestom"
vibenilla.license = License.MIT

dependencies {
    compileOnly(libs.fastutil)

    testImplementation(libs.minestom.testing)
}
