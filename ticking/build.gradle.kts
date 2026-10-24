plugins {
    id("vibenilla.library")
}

description = "Random block ticks for Minestom"
vibenilla.license = License.MIT

dependencies {
    compileOnly(libs.fastutil)
    implementation(project(":worldgen"))

    testImplementation(libs.minestom.testing)
}
