import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
    `maven-publish`
}

description = "A library for Minestom placement"
group = "rocks.minestom"

val minestomVersion = "26_3-20261007.224755-10"
val mcVersion = "26.3"
val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
version = "$date-$mcVersion"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name = project.name
                description = project.description
                url = "https://github.com/vibenilla/placement"

                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        name = "mudkip"
                        id = "mudkjp"
                        email = "mudkip@mudkip.dev"
                        url = "https://mudkip.dev"
                    }
                }

                scm {
                    url = "https://github.com/vibenilla/placement"
                    connection = "scm:git:git://github.com/vibenilla/placement.git"
                    developerConnection = "scm:git:ssh://git@github.com/vibenilla/placement.git"
                }
            }
        }
    }

    repositories {
        maven {
            name = "skylite"
            url = uri("https://maven.skylite.gg/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

repositories {
    mavenCentral()

    maven("https://central.sonatype.com/repository/maven-snapshots/") {
        content {
            includeModule("net.minestom", "minestom")
            includeModule("net.minestom", "testing")
        }
    }
}

dependencies {
    implementation("net.minestom:minestom:$minestomVersion")

    // Unit testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")

    testImplementation("it.unimi.dsi:fastutil:8.5.18")
    testImplementation("org.tinylog:tinylog-api:2.8.0-M1")
    testImplementation("org.tinylog:tinylog-impl:2.8.0-M1")
    testImplementation("org.tinylog:slf4j-tinylog:2.8.0-M1")
}

tasks.test {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}
