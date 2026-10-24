plugins {
    id("vibenilla.java")
    application
}

description = "Vanilla survival server built from the vibenilla modules"

dependencies {
    implementation(libs.minestom)
    implementation(libs.slf4j.api)
    implementation(project(":pvp"))
    implementation(project(":worldgen"))
    implementation(project(":placement"))
    implementation(project(":crafting"))
    implementation(project(":fluids"))
    implementation(project(":loot"))
    implementation(project(":containers"))

    runtimeOnly(libs.bundles.tinylog)
}

application {
    mainClass = "dev.oglass.vibenilla.server.Server"
}

val runDirectory = layout.projectDirectory.dir("run")

val vanillaData =
    tasks.register<VanillaDataTask>("vanillaData") {
        minecraftVersion = libs.versions.minecraft
        outputDirectory = runDirectory.dir("vanilla")
    }

tasks.named<JavaExec>("run") {
    dependsOn(vanillaData)
    workingDir = runDirectory.asFile
    standardInput = System.`in`
    systemProperties(providers.systemPropertiesPrefixedBy("vibenilla.").get())
}
