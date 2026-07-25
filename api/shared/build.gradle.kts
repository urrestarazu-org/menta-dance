plugins {
    id("java-library")
}

description = "Shared contracts and interfaces between modules"

dependencies {
    // No Spring Boot dependencies here - this is a pure Java module

    // Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Validation API
    implementation("jakarta.validation:jakarta.validation-api:3.1.0")
}

tasks.jar {
    enabled = true
}
