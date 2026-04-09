plugins {
    id("buildlogic.kotlin-spring-common-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(platform("io.mongock:mongock-bom:5.5.1"))
    implementation("io.mongock:mongock-springboot")
    implementation("io.mongock:mongodb-springdata-v3-driver")

    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.3")
    testImplementation("io.mongock:mongock-standalone")
}