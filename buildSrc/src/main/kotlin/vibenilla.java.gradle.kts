import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    java
    id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

fun library(alias: String) = libs.findLibrary(alias).get()

val minecraftVersion = libs.findVersion("minecraft").get().requiredVersion
val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

group = "dev.oglass.vibenilla"
version = "$date-$minecraftVersion"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    testImplementation(platform(library("junit-bom")))
    testImplementation(library("junit-jupiter"))
    testRuntimeOnly(library("junit-platform-launcher"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("minestom.inside-test", "true")
    failOnNoDiscoveredTests = false
}

spotless {
    java {
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion).aosp()
        removeUnusedImports()
    }
}
