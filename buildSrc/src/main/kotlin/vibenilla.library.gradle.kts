import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
    `maven-publish`
    id("com.diffplug.spotless")
}

val vibenilla = extensions.create<VibenillaExtension>("vibenilla")
val libs = versionCatalogs.named("libs")

fun library(alias: String) = libs.findLibrary(alias).get()

val minecraftVersion = libs.findVersion("minecraft").get().requiredVersion
val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

group = "dev.oglass.vibenilla"
version = "$date-$minecraftVersion"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly(library("minestom"))

    testImplementation(library("minestom"))
    testImplementation(platform(library("junit-bom")))
    testImplementation(library("junit-jupiter"))
    testRuntimeOnly(library("junit-platform-launcher"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("minestom.inside-test", "true")
    failOnNoDiscoveredTests = false
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

spotless {
    java {
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion).aosp()
        removeUnusedImports()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name = project.name
                description = provider { project.description }
                url = "https://github.com/oglassdev/vibenilla-mono"

                licenses {
                    license {
                        name = vibenilla.license.map { it.id }
                        url = vibenilla.license.map { it.url }
                    }
                }

                developers {
                    developer {
                        id = "mudkjp"
                        name = "mudkip"
                        url = "https://mudkip.dev"
                    }
                    developer {
                        id = "oglass"
                        name = "oglass"
                    }
                }

                scm {
                    url = "https://github.com/oglassdev/vibenilla-mono"
                    connection = "scm:git:https://github.com/oglassdev/vibenilla-mono.git"
                    developerConnection = "scm:git:ssh://git@github.com/oglassdev/vibenilla-mono.git"
                }
            }
        }
    }
}
