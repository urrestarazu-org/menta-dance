plugins {
    id("java-library")
}

description = "Authentication and user management module"

dependencies {
    // Shared module
    implementation(project(":api:shared"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

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
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.jar {
    enabled = true
}

// TODO Phase 1: Uncomment when implementing tests
// Higher coverage for auth module (100% for domain and application)
// tasks.jacocoTestCoverageVerification {
//     violationRules {
//         rule {
//             element = "CLASS"
//             limit {
//                 counter = "LINE"
//                 value = "COVEREDRATIO"
//                 minimum = "1.00".toBigDecimal()
//             }
//             includes = listOf(
//                 "com.menta.auth.domain.*",
//                 "com.menta.auth.application.*"
//             )
//         }
//         rule {
//             element = "CLASS"
//             limit {
//                 counter = "LINE"
//                 value = "COVEREDRATIO"
//                 minimum = "0.50".toBigDecimal()
//             }
//             includes = listOf("com.menta.auth.infrastructure.*")
//         }
//     }
// }
