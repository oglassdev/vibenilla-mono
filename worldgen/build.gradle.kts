plugins {
    id("vibenilla.library")
}

description = "World generation for Minestom"
vibenilla.license = License.APACHE_2

dependencies {
    compileOnly(libs.slf4j.api)

    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.slf4j.simple)
}
