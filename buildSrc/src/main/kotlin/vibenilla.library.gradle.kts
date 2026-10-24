plugins {
    id("vibenilla.java")
    `java-library`
    `maven-publish`
}

val vibenilla = extensions.create<VibenillaExtension>("vibenilla")
val libs = versionCatalogs.named("libs")

fun library(alias: String) = libs.findLibrary(alias).get()

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly(library("minestom"))

    testImplementation(library("minestom"))
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
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
