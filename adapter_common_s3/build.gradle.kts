plugins {
    id("buildlogic.kotlin-library-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("software.amazon.awssdk:s3:2.42.30")
}
