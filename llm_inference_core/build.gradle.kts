plugins {
    id("buildlogic.kotlin-library-conventions")
}

dependencies {
    implementation("dev.langchain4j:langchain4j:1.14.1")
    implementation("dev.langchain4j:langchain4j-open-ai:1.14.1")
    implementation("org.slf4j:slf4j-api:2.0.18")

    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.18")
}
