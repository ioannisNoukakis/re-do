plugins {
    id("buildlogic.kotlin-library-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation("com.openai:openai-java:4.35.0")
}
