plugins {
    id("buildlogic.kotlin-task-plugin-conventions")
}

dependencies {
    implementation("com.openai:openai-java:4.35.0")
    testImplementation(project(":task_handler_test_support"))
}
