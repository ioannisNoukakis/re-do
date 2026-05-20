plugins {
    id("buildlogic.kotlin-task-plugin-conventions")
}

dependencies {
    implementation(project(":llm_inference_core"))
    testImplementation(project(":task_handler_test_support"))
}
