plugins {
    id("vibenilla.library")
}

description = "Crafting for Minestom"
vibenilla.license = License.APACHE_2

dependencies {
    compileOnly(libs.fastutil)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.fastutil)
}

// Tests read vanilla recipe data from data/minecraft, which isn't checked in.
tasks.test {
    enabled = false
}
