plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "buildSrc/**/*.gradle.kts")
        ktlint()
    }
    kotlin {
        target("buildSrc/src/**/*.kt")
        ktlint()
    }
}
