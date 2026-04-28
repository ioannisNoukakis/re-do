plugins {
    id("buildlogic.kotlin-library-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("software.amazon.awssdk:s3:2.42.30")
    implementation("software.amazon.awssdk:s3-transfer-manager:2.42.30")
    implementation("org.apache.tika:tika-core:3.3.0")
}
