plugins {
    id("com.diffplug.spotless")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        ktlint()
            .editorConfigOverride(
                mapOf("ktlint_standard_package-name" to "disabled")
            )

        tableTestFormatter()
    }
    kotlinGradle {
        ktlint()
    }
}