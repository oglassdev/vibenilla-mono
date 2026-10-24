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

    runtimeOnly(libs.bundles.tinylog)
}

application {
    mainClass = "dev.oglass.vibenilla.server.Server"
}

val runDirectory = layout.projectDirectory.dir("run")

val vanillaData =
    tasks.register<VanillaDataTask>("vanillaData") {
        minecraftVersion = libs.versions.minecraft.asProvider()
        outputDirectory = runDirectory.dir("vanilla")
    }

val worldgenData =
    tasks.register<VanillaDataTask>("worldgenData") {
        minecraftVersion = libs.versions.minecraft.worldgen
        outputDirectory = runDirectory.dir("vanilla-worldgen")
    }

tasks.named<JavaExec>("run") {
    dependsOn(vanillaData, worldgenData)
    workingDir = runDirectory.asFile
    standardInput = System.`in`
    systemProperties(providers.systemPropertiesPrefixedBy("vibenilla.").get())
}
