plugins {
    id("buildlogic.kotlin-spring-common-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adapter_common_rabbitmq_spring"))
    implementation(project(":adapter_common_s3"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("software.amazon.awssdk:s3:2.42.30")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
