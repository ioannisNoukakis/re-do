plugins {
    id("buildlogic.kotlin-spring-common-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adapter_common_rabbitmq_spring"))
    implementation(project(":adapter_common_mongodb_spring"))
    implementation(project(":adapter_common_s3"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("software.amazon.awssdk:s3:2.42.30")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
}
