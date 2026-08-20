import com.menta.buildlogic.registerLayeredCoverageVerification

plugins {
    id("java-library")
}

description = "Physical classes module"

dependencies {
    // Shared module
    implementation(project(":api:shared"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // First Physical endpoint that reads the authenticated caller (#42):
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
// Migrated off a flat element = CLASS 0.80 for the same reason as
// :api:virtual — see that module's note.
//
//   - Domain + Application: 0.95 LINE (BUNDLE). Real: 97.8%.
//   - Infrastructure: 0.90 LINE (BUNDLE). Real: 97.3%.
val jacocoDomainApplicationCoverageVerification = registerLayeredCoverageVerification(
    "jacocoDomainApplicationCoverageVerification", "0.95",
    listOf("com/menta/physical/domain/**", "com/menta/physical/application/**")
)
val jacocoInfrastructureCoverageVerification = registerLayeredCoverageVerification(
    "jacocoInfrastructureCoverageVerification", "0.90",
    listOf("com/menta/physical/infrastructure/**")
)

tasks.check {
    dependsOn(jacocoDomainApplicationCoverageVerification, jacocoInfrastructureCoverageVerification)
}
