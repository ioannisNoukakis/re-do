plugins {
    id("buildlogic.kotlin-spring-common-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adapter_common_rabbitmq_spring"))

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}