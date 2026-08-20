import com.menta.buildlogic.registerLayeredCoverageVerification

plugins {
    id("java-library")
}

description = "Virtual courses module"

dependencies {
    // Shared module
    implementation(project(":api:shared"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // First Virtual endpoint that reads the authenticated caller (#54):
    // Authentication is a Spring Security type, resolved as a controller
    // method argument. Already on the runtime classpath via :api:app /
    // :api:auth; needed here too so this module compiles on its own.
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")

    // MapStruct for mapping between layers
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    // Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.jar {
    enabled = true
}

// Coverage gates. The mechanism lives in buildSrc's
// registerLayeredCoverageVerification; what stays here is the policy.
//
// This module used to gate with element = CLASS at a flat 0.80 across
// com.menta.virtual.*, which was wrong in both directions: the aggregate sat
// at 97.9% — so a third of the suite could be deleted in silence — while any
// individual thin class dragged the build down. That is exactly what
// happened to the Transactional*UseCase decorators in #112: ten one-line
// delegators, each below 0.80 on its own, against a layer already at 97%.
// BUNDLE per layer asks the question that actually matters.
//
//   - Domain + Application: 0.95 LINE (BUNDLE). Real: 98.7%.
//   - Infrastructure: 0.90 LINE (BUNDLE). Real: 97.0%.
val jacocoDomainApplicationCoverageVerification = registerLayeredCoverageVerification(
    "jacocoDomainApplicationCoverageVerification", "0.95",
    listOf("com/menta/virtual/domain/**", "com/menta/virtual/application/**")
)
val jacocoInfrastructureCoverageVerification = registerLayeredCoverageVerification(
    "jacocoInfrastructureCoverageVerification", "0.90",
    listOf("com/menta/virtual/infrastructure/**")
)

tasks.check {
    dependsOn(jacocoDomainApplicationCoverageVerification, jacocoInfrastructureCoverageVerification)
}
