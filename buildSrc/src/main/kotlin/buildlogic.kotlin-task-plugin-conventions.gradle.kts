import org.gradle.kotlin.dsl.`java-library`

plugins {
    id("buildlogic.kotlin-common-conventions")
    `java-library`
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
}

tasks.named("jar") {
    // Disable the plain jar; consumers will use shadowJar instead.
    enabled = false
}
