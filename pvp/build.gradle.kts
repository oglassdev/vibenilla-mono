plugins {
    id("vibenilla.library")
}

description = "Combat for Minestom"
vibenilla.license = License.APACHE_2

dependencies {
    compileOnly(libs.fastutil)

    testImplementation(libs.minestom.testing)
}
