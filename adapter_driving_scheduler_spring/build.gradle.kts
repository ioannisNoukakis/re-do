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

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
