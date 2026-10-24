plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "buildSrc/src/**/*.gradle.kts")
        ktlint()
    }
    kotlin {
        target("buildSrc/src/**/*.kt")
        ktlint()
    }
}
