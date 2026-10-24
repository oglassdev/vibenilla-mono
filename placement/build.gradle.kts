plugins {
    id("vibenilla.library")
}

description = "Block placement for Minestom"
vibenilla.license = License.APACHE_2

dependencies {
    testImplementation(libs.fastutil)
    testImplementation(libs.bundles.tinylog)
}
